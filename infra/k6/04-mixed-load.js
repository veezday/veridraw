import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// =============================================================================
// КОНФИГУРАЦИЯ
// =============================================================================
const CONFIG = {
    keycloakUrl: 'http://keycloak:8080',
    drawCoreUrl: 'http://draw-core:8082',
    realm: 'veridraw',
    clientId: 'veridraw-api',
    clientSecret: 'veridraw-api-secret-key-2026',

    masterAdminUser: 'admin',
    masterAdminPass: 'admin',
    demoAdminUser: 'demo-admin',
    demoAdminPass: 'admin123',

    adminPrefix: 'lt_admin_',
    testPass: 'password123',

    numUsers: 150,
    sleepDuration: 0.2
};

// =============================================================================
// МЕТРИКИ
// =============================================================================
const api5xxErrors = new Rate('flash_sale_5xx_errors');
const p95Latency = new Trend('flash_sale_p95_latency_ms');
const successfulPurchases = new Rate('flash_sale_purchase_success_rate');

// =============================================================================
// УТИЛИТЫ
// =============================================================================
function getRandomIp() {
    return `10.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`;
}

export const options = {
    scenarios: {
        flash_sale: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },
                { duration: '30s', target: 150 },
                { duration: '20s', target: 0 },
            ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: {
        'flash_sale_5xx_errors': ['rate<0.01'],
        'flash_sale_purchase_success_rate': ['rate>0.70']
    },
};

// =============================================================================
// SETUP: Инициализация данных
// =============================================================================
export function setup() {
    console.log(`[INFO] Начало этапа SETUP: подготовка пользователей и целевого розыгрыша для проверки конкуренции`);

    const adminRes = http.post(`${CONFIG.keycloakUrl}/realms/master/protocol/openid-connect/token`,
        `grant_type=password&client_id=admin-cli&username=${CONFIG.masterAdminUser}&password=${CONFIG.masterAdminPass}`, {
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        });

    if (adminRes.status !== 200) {
        console.error(`[ERROR] Не удалось получить токен администратора.`);
        return { adminTokens: [], flashSaleDrawId: null };
    }

    const adminHeaders = {
        'Authorization': `Bearer ${adminRes.json('access_token')}`,
        'Content-Type': 'application/json'
    };

    const adminTokens = [];
    for (let i = 1; i <= CONFIG.numUsers; i++) {
        const username = `${CONFIG.adminPrefix}${i}`;
        const token = ensureUserWithRole(username, 'ADMIN', adminHeaders);
        if (token) adminTokens.push(token);
    }

    let flashSaleDrawId = null;
    const demoTokenRes = http.post(`${CONFIG.keycloakUrl}/realms/${CONFIG.realm}/protocol/openid-connect/token`,
        `grant_type=password&client_id=${CONFIG.clientId}&client_secret=${CONFIG.clientSecret}&username=${CONFIG.demoAdminUser}&password=${CONFIG.demoAdminPass}`, {
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        });

    if (demoTokenRes.status === 200) {
        const demoToken = demoTokenRes.json('access_token');
        const drawPayload = JSON.stringify({
            name: `FLASH_SALE_DRAW_${Date.now()}`,
            description: 'High contention test draw for concurrent ticket purchasing',
            maxTickets: 50000,
            ticketPrice: 100.0
        });

        const createRes = http.post(`${CONFIG.drawCoreUrl}/api/draws`, drawPayload, {
            headers: { 'Authorization': `Bearer ${demoToken}`, 'Content-Type': 'application/json' }
        });

        if (createRes.status === 201) {
            flashSaleDrawId = createRes.json('id');
            http.post(`${CONFIG.drawCoreUrl}/api/draws/${flashSaleDrawId}/activate`, null, {
                headers: { 'Authorization': `Bearer ${demoToken}`, 'Content-Type': 'application/json' }
            });
            console.log(`[INFO] Целевой розыгрыш создан и активирован. ID: ${flashSaleDrawId}`);
        } else {
            console.error(`[ERROR] Сбой создания розыгрыша. Status: ${createRes.status}, Body: ${createRes.body}`);
        }
    } else {
        console.error(`[ERROR] Не удалось получить токен для пользователя ${CONFIG.demoAdminUser}.`);
    }

    return { adminTokens, flashSaleDrawId };
}

function ensureUserWithRole(username, role, adminHeaders) {
    // Этап 1: Создание пользователя (статус 409 Conflict игнорируется как ожидаемый)
    const createRes = http.post(`${CONFIG.keycloakUrl}/admin/realms/${CONFIG.realm}/users`, JSON.stringify({
        username: username, enabled: true, email: `${username}@veridraw.local`,
        firstName: 'LoadTest', lastName: role, emailVerified: true, requiredActions: [],
        credentials: [{ type: 'password', value: CONFIG.testPass, temporary: false }]
    }), { headers: adminHeaders });

    if (createRes.status !== 201 && createRes.status !== 409) return null;

    // Этап 2: Получение идентификатора пользователя
    const searchRes = http.get(`${CONFIG.keycloakUrl}/admin/realms/${CONFIG.realm}/users?username=${username}&exact=true`, { headers: adminHeaders });
    if (searchRes.status !== 200 || searchRes.json().length === 0) return null;
    const userId = searchRes.json()[0].id;

    // Этап 3: Получение метаданных роли
    const roleRes = http.get(`${CONFIG.keycloakUrl}/admin/realms/${CONFIG.realm}/roles/${role}`, { headers: adminHeaders });
    if (roleRes.status !== 200) return null;

    // Этап 4: Назначение роли пользователю
    const assignRes = http.post(
        `${CONFIG.keycloakUrl}/admin/realms/${CONFIG.realm}/users/${userId}/role-mappings/realm`,
        JSON.stringify([roleRes.json()]),
        { headers: adminHeaders }
    );
    if (assignRes.status !== 204 && assignRes.status !== 409) return null;

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
    if (!data || data.adminTokens.length === 0 || !data.flashSaleDrawId) {
        console.error("[ERROR] Отсутствуют данные инициализации.");
        sleep(1);
        return;
    }

    const roll = Math.random();
    let logCounter = 0;

    function recordMetrics(res, expectedStatuses, scenarioName) {
        const isExpected = expectedStatuses.includes(res.status);
        check(res, { [`${scenarioName}: ожидаемый статус ${expectedStatuses.join(' или ')}`]: (r) => isExpected });

        if (res.status >= 500) {
            api5xxErrors.add(1);
        } else if (isExpected) {
            p95Latency.add(res.timings.duration);

            if (scenarioName.includes('Buy Ticket')) {
                successfulPurchases.add(res.status === 201);
            }
        } else {
            if (logCounter < 5) {
                console.warn(`[DIAGNOSTIC] ${scenarioName} | Ожидалось: ${expectedStatuses.join(',')} | Получено: ${res.status} | Тело: ${res.body.substring(0, 150)}`);
                logCounter++;
            }
        }
    }

    // Сценарий 1 (85%): Массовая покупка билетов в один розыгрыш. Создает максимальную конкуренцию за строку в БД.
    if (roll < 0.85) {
        const payload = JSON.stringify({
            drawId: data.flashSaleDrawId,
            participantName: `User_${Math.random().toString(36).substring(7)}`,
            participantEmail: `flash_${Date.now()}_${Math.random().toString(36).substring(7)}@test.com`
        });

        const res = http.post(`${CONFIG.drawCoreUrl}/api/tickets`, payload, {
            headers: {
                'Authorization': `Bearer ${data.adminTokens[Math.floor(Math.random() * data.adminTokens.length)]}`,
                'Content-Type': 'application/json',
                'X-Forwarded-For': getRandomIp()
            }
        });

        recordMetrics(res, [201, 400], "Flash Sale: Buy Ticket");
    }
    // Сценарий 2 (10%): Фоновое чтение данных (имитация обновления страницы пользователем)
    else if (roll < 0.95) {
        const res = http.get(`${CONFIG.drawCoreUrl}/api/draws`, {
            headers: { 'Authorization': `Bearer ${data.adminTokens[0]}`, 'Content-Type': 'application/json' }
        });
        recordMetrics(res, [200], "Background: Read Draws");
    }
    // Сценарий 3 (5%): Попытка покупки билета с дублирующимся email для проверки обработки DataIntegrityViolationException
    else {
        const payload = JSON.stringify({
            drawId: data.flashSaleDrawId,
            participantName: 'Duplicate Tester',
            participantEmail: 'duplicate_flash_sale@test.com'
        });
        const res = http.post(`${CONFIG.drawCoreUrl}/api/tickets`, payload, {
            headers: { 'Authorization': `Bearer ${data.adminTokens[0]}`, 'Content-Type': 'application/json' }
        });
        recordMetrics(res, [201, 400], "Edge Case: Duplicate Email Purchase");
    }

    sleep(CONFIG.sleepDuration);
}