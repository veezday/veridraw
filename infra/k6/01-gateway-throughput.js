import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// =============================================================================
// КОНФИГУРАЦИЯ
// =============================================================================
const CONFIG = {
    keycloakUrl: 'http://keycloak:8080',
    gatewayUrl: 'http://gateway:8081',
    realm: 'veridraw',
    clientId: 'veridraw-api',
    clientSecret: 'veridraw-api-secret-key-2026',

    masterAdminUser: 'admin',
    masterAdminPass: 'admin',

    adminPrefix: 'lt_admin_',
    userPrefix: 'lt_user_',
    testPass: 'password123',

    numUsers: 100,
    sleepDuration: 0.5
};

// =============================================================================
// МЕТРИКИ
// =============================================================================
const gateway401Rate = new Rate('gateway_401_unauthorized');
const gateway429Rate = new Rate('gateway_429_rate_limited');
const gateway5xxRate = new Rate('gateway_5xx_errors');
const authLatency = new Trend('gateway_auth_latency_ms');
const rateLimitStressHits = new Counter('gateway_ratelimit_stress_hits');

// =============================================================================
// УТИЛИТЫ
// =============================================================================
function getRandomIp() {
    return `10.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`;
}

export const options = {
    stages: [
        { duration: '15s', target: 30 },
        { duration: '45s', target: 100 },
        { duration: '15s', target: 0 },
    ],
    thresholds: {
        'gateway_5xx_errors': ['rate<0.01'],
        'gateway_auth_latency_ms': ['p(95)<100'],
    },
};

// =============================================================================
// SETUP: Инициализация данных
// =============================================================================
export function setup() {
    console.log(`[INFO] Начало этапа SETUP: проверка или создание ${CONFIG.numUsers} пользователей`);

    const adminRes = http.post(`${CONFIG.keycloakUrl}/realms/master/protocol/openid-connect/token`,
        `grant_type=password&client_id=admin-cli&username=${CONFIG.masterAdminUser}&password=${CONFIG.masterAdminPass}`, {
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        });

    if (adminRes.status !== 200) {
        console.error(`[ERROR] Не удалось получить токен администратора. Status: ${adminRes.status}`);
        return { tokens: [] };
    }

    const adminHeaders = {
        'Authorization': `Bearer ${adminRes.json('access_token')}`,
        'Content-Type': 'application/json'
    };

    const userTokens = [];

    for (let i = 1; i <= CONFIG.numUsers; i++) {
        const isAdmin = i % 10 !== 0;
        const role = isAdmin ? 'ADMIN' : 'USER';
        const prefix = isAdmin ? CONFIG.adminPrefix : CONFIG.userPrefix;
        const username = `${prefix}${i}`;

        const token = ensureUserWithRole(username, role, adminHeaders);

        if (token && !isAdmin) {
            userTokens.push(token);
        }
    }

    console.log(`[INFO] Этап SETUP завершен. Получено токенов USER: ${userTokens.length}`);
    return { tokens: userTokens };
}

function ensureUserWithRole(username, role, adminHeaders) {
    // Этап 1: Создание пользователя (статус 409 Conflict игнорируется как ожидаемый)
    const createRes = http.post(`${CONFIG.keycloakUrl}/admin/realms/${CONFIG.realm}/users`, JSON.stringify({
        username: username, enabled: true, email: `${username}@veridraw.local`,
        firstName: 'LoadTest', lastName: role, emailVerified: true, requiredActions: [],
        credentials: [{ type: 'password', value: CONFIG.testPass, temporary: false }]
    }), { headers: adminHeaders });

    if (createRes.status !== 201 && createRes.status !== 409) {
        console.error(`[ERROR] Сбой создания пользователя ${username}. Status: ${createRes.status}`);
        return null;
    }

    // Этап 2: Получение идентификатора пользователя
    const searchRes = http.get(`${CONFIG.keycloakUrl}/admin/realms/${CONFIG.realm}/users?username=${username}&exact=true`, { headers: adminHeaders });
    if (searchRes.status !== 200 || searchRes.json().length === 0) return null;
    const userId = searchRes.json()[0].id;

    // Этап 3: Получение метаданных роли
    const roleRes = http.get(`${CONFIG.keycloakUrl}/admin/realms/${CONFIG.realm}/roles/${role}`, { headers: adminHeaders });
    if (roleRes.status !== 200) return null;
    const roleData = roleRes.json();

    // Этап 4: Назначение роли пользователю
    const assignRes = http.post(
        `${CONFIG.keycloakUrl}/admin/realms/${CONFIG.realm}/users/${userId}/role-mappings/realm`,
        JSON.stringify([roleData]),
        { headers: adminHeaders }
    );

    if (assignRes.status !== 204 && assignRes.status !== 409) {
        console.error(`[ERROR] Сбой назначения роли ${role} пользователю ${username}. Status: ${assignRes.status}`);
        return null;
    }

    // Этап 5: Получение токена доступа
    const tokenRes = http.post(`${CONFIG.keycloakUrl}/realms/${CONFIG.realm}/protocol/openid-connect/token`,
        `grant_type=password&client_id=${CONFIG.clientId}&client_secret=${CONFIG.clientSecret}&username=${username}&password=${CONFIG.testPass}`, {
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        });

    return tokenRes.status === 200 ? tokenRes.json('access_token') : null;
}

// =============================================================================
// DEFAULT: Сценарии нагрузки
// =============================================================================
export default function (data) {
    if (!data || data.tokens.length === 0) {
        console.error("[ERROR] Отсутствуют токены. Проверьте логи этапа SETUP.");
        sleep(1);
        return;
    }

    const randomIp = getRandomIp();
    const randomToken = data.tokens[Math.floor(Math.random() * data.tokens.length)];
    const roll = Math.random();

    function recordMetrics(res, expectedStatuses, scenarioName) {
        const isExpected = expectedStatuses.includes(res.status);

        check(res, {
            [`${scenarioName}: ожидаемый статус ${expectedStatuses.join(' или ')}`]: (r) => isExpected
        });

        if (res.status >= 500) {
            gateway5xxRate.add(1);
        }

        if (res.status === 429) {
            gateway429Rate.add(1);
        }

        if (isExpected) {
            authLatency.add(res.timings.duration);
        }
    }

    // Сценарий 1 (25%): Валидный запрос с токеном пользователя
    if (roll < 0.25) {
        recordMetrics(
            http.get(`${CONFIG.gatewayUrl}/api/draws`, {
                headers: { 'Authorization': `Bearer ${randomToken}`, 'X-Forwarded-For': randomIp, 'X-Real-IP': randomIp }
            }),
            [200, 403, 429], "Valid Request"
        );
    }
    // Сценарий 2 (20%): Запрос без токена авторизации
    else if (roll < 0.45) {
        recordMetrics(
            http.get(`${CONFIG.gatewayUrl}/api/draws`, {
                headers: { 'X-Forwarded-For': randomIp, 'X-Real-IP': randomIp }
            }),
            [401, 429], "No Token"
        );
    }
    // Сценарий 3 (15%): Запрос с невалидным токеном
    else if (roll < 0.60) {
        recordMetrics(
            http.get(`${CONFIG.gatewayUrl}/api/tickets`, {
                headers: { 'Authorization': 'Bearer invalid_token_string_xyz', 'X-Forwarded-For': randomIp }
            }),
            [401, 429], "Invalid Token"
        );
    }
    // Сценарий 4 (15%): Запрос к публичному эндпоинту (Health)
    else if (roll < 0.75) {
        recordMetrics(
            http.get(`${CONFIG.gatewayUrl}/actuator/health`, {
                headers: { 'X-Forwarded-For': randomIp }
            }),
            [200], "Public Health"
        );
    }
    // Сценарий 5 (10%): Запрос к несуществующему маршруту
    else if (roll < 0.85) {
        recordMetrics(
            http.get(`${CONFIG.gatewayUrl}/api/secret-route`, {
                headers: { 'Authorization': `Bearer ${randomToken}` }
            }),
            [403, 429], "Unknown Route"
        );
    }
    // Сценарий 6 (15%): Проверка срабатывания Rate Limiter
    else {
        const fixedIp = '192.168.200.200';
        const headers = { 'Authorization': `Bearer ${randomToken}`, 'X-Forwarded-For': fixedIp, 'X-Real-IP': fixedIp };
        let hit429 = false;

        for (let i = 0; i < 15; i++) {
            const res = http.get(`${CONFIG.gatewayUrl}/api/draws`, { headers });
            if (res.status === 429) {
                hit429 = true;
                rateLimitStressHits.add(1);
                break;
            }
        }

        check({ status: hit429 ? 429 : 200 }, { 'Stress Test: срабатывание лимитера (429)': (r) => r.status === 429 });
        if (hit429) gateway429Rate.add(1);
    }

    sleep(CONFIG.sleepDuration);
}