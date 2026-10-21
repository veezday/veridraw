import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// =============================================================================
// КОНФИГУРАЦИЯ
// =============================================================================
const CONFIG = {
    keycloakUrl: 'http://keycloak:8080',
    notifyWorkerUrl: 'http://notify-worker:8083',
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
const server5xxErrors = new Rate('notify_worker_5xx_errors');
const p95Latency = new Trend('notify_worker_p95_latency_ms');

// =============================================================================
// УТИЛИТЫ
// =============================================================================
function getRandomIp() {
    return `10.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`;
}

export const options = {
    stages: [
        { duration: '10s', target: 20 },
        { duration: '40s', target: 100 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        'notify_worker_5xx_errors': ['rate<0.01'],
        'notify_worker_p95_latency_ms': ['p(95)<200'],
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
        return { adminTokens: [], userTokens: [] };
    }

    const adminHeaders = {
        'Authorization': `Bearer ${adminRes.json('access_token')}`,
        'Content-Type': 'application/json'
    };

    const adminTokens = [];
    const userTokens = [];

    for (let i = 1; i <= CONFIG.numUsers; i++) {
        const isAdmin = i % 10 !== 0;
        const role = isAdmin ? 'ADMIN' : 'USER';
        const prefix = isAdmin ? CONFIG.adminPrefix : CONFIG.userPrefix;
        const username = `${prefix}${i}`;

        const token = ensureUserWithRole(username, role, adminHeaders);

        if (token) {
            if (isAdmin) adminTokens.push(token);
            else userTokens.push(token);
        }
    }

    console.log(`[INFO] Этап SETUP завершен. Токенов ADMIN: ${adminTokens.length}, USER: ${userTokens.length}`);
    return { adminTokens, userTokens };
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
    if (!data || data.adminTokens.length === 0) {
        console.error("[ERROR] Отсутствуют токены. Проверьте логи этапа SETUP.");
        sleep(1);
        return;
    }

    const randomIp = getRandomIp();
    const getRandomAdminToken = () => data.adminTokens[Math.floor(Math.random() * data.adminTokens.length)];
    const getRandomUserToken = () => data.userTokens.length > 0 ? data.userTokens[Math.floor(Math.random() * data.userTokens.length)] : data.adminTokens[0];

    const roll = Math.random();
    let logCounter = 0;

    function recordMetrics(res, expectedStatuses, scenarioName) {
        const isExpected = expectedStatuses.includes(res.status);
        check(res, { [`${scenarioName}: ожидаемый статус ${expectedStatuses.join(' или ')}`]: (r) => isExpected });

        if (res.status >= 500) {
            server5xxErrors.add(1);
        } else if (isExpected) {
            p95Latency.add(res.timings.duration);
        } else {
            if (logCounter < 3) {
                console.warn(`[DIAGNOSTIC] ${scenarioName} | Ожидалось: ${expectedStatuses.join(',')} | Получено: ${res.status} | Тело: ${res.body.substring(0, 100)}`);
                logCounter++;
            }
        }
    }

    // Сценарий 1 (40%): Получение списка необработанных событий (роль ADMIN)
    if (roll < 0.40) {
        const headers = { 'Authorization': `Bearer ${getRandomAdminToken()}`, 'Content-Type': 'application/json', 'X-Forwarded-For': randomIp, 'X-Real-IP': randomIp };
        const res = http.get(`${CONFIG.notifyWorkerUrl}/api/inbox/unpublished`, { headers });
        recordMetrics(res, [200], "Get Unpublished (200)");
    }
    // Сценарий 2 (30%): Инициация повторной отправки событий (роль ADMIN)
    else if (roll < 0.70) {
        const headers = { 'Authorization': `Bearer ${getRandomAdminToken()}`, 'Content-Type': 'application/json', 'X-Forwarded-For': randomIp, 'X-Real-IP': randomIp };
        const res = http.post(`${CONFIG.notifyWorkerUrl}/api/inbox/resend`, null, { headers });
        recordMetrics(res, [200], "Resend Trigger (200)");
    }
    // Сценарий 3 (15%): Проверка отказа в доступе для роли USER (ошибка 403)
    else if (roll < 0.85) {
        const headers = { 'Authorization': `Bearer ${getRandomUserToken()}`, 'Content-Type': 'application/json' };
        const res = http.get(`${CONFIG.notifyWorkerUrl}/api/inbox/unpublished`, { headers });
        recordMetrics(res, [403], "User Get Unpublished (403)");
    }
    // Сценарий 4 (15%): Проверка отказа в доступе при отсутствии токена (ошибка 401)
    else {
        const headers = { 'Content-Type': 'application/json', 'X-Forwarded-For': randomIp, 'X-Real-IP': randomIp };
        const res = http.post(`${CONFIG.notifyWorkerUrl}/api/inbox/resend`, null, { headers });
        recordMetrics(res, [401], "No Token Resend (401)");
    }

    sleep(CONFIG.sleepDuration);
}