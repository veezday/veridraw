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
    userPrefix: 'lt_user_',
    testPass: 'password123',

    numUsers: 100,
    sleepDuration: 0.3
};

// =============================================================================
// МЕТРИКИ
// =============================================================================
const server5xxErrors = new Rate('draw_core_5xx_errors');
const p95Latency = new Trend('draw_core_p95_latency_ms');
const successfulCompletions = new Rate('draw_core_successful_completions');

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
        'draw_core_5xx_errors': ['rate<0.01'],
        'draw_core_successful_completions': ['rate>0.95']
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
        return { adminTokens: [], userTokens: [], globalActiveDrawId: null };
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

    let globalActiveDrawId = null;
    const demoTokenRes = http.post(`${CONFIG.keycloakUrl}/realms/${CONFIG.realm}/protocol/openid-connect/token`,
        `grant_type=password&client_id=${CONFIG.clientId}&client_secret=${CONFIG.clientSecret}&username=${CONFIG.demoAdminUser}&password=${CONFIG.demoAdminPass}`, {
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        });

    if (demoTokenRes.status === 200) {
        const demoToken = demoTokenRes.json('access_token');
        const headers = { 'Authorization': `Bearer ${demoToken}`, 'Content-Type': 'application/json' };

        const drawRes = http.post(`${CONFIG.drawCoreUrl}/api/draws`, JSON.stringify({
            name: `Global Active Draw ${Date.now()}`,
            description: 'For pure ticket purchase load testing',
            maxTickets: 100000,
            ticketPrice: 100.0
        }), { headers });

        if (drawRes.status === 201) {
            globalActiveDrawId = drawRes.json('id');
            http.post(`${CONFIG.drawCoreUrl}/api/draws/${globalActiveDrawId}/activate`, null, { headers });
        }
    }

    console.log(`[INFO] Этап SETUP завершен. Токенов ADMIN: ${adminTokens.length}, USER: ${userTokens.length}`);
    return { adminTokens, userTokens, globalActiveDrawId };
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
// ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ДЛЯ WORKFLOW
// =============================================================================

function createAndActivateDraw(adminToken) {
    const uniqueName = `WorkflowDraw_${Date.now()}_${Math.random().toString(36).substring(7)}`;
    const headers = { 'Authorization': `Bearer ${adminToken}`, 'Content-Type': 'application/json' };

    const createRes = http.post(`${CONFIG.drawCoreUrl}/api/draws`, JSON.stringify({
        name: uniqueName, description: 'Workflow test draw', maxTickets: 100, ticketPrice: 50.0
    }), { headers });

    if (createRes.status === 201) {
        const drawId = createRes.json('id');
        const activateRes = http.post(`${CONFIG.drawCoreUrl}/api/draws/${drawId}/activate`, null, { headers });
        if (activateRes.status === 200) {
            return drawId;
        }
    }
    return null;
}

function buyTicketStrict(drawId, adminToken, email) {
    const headers = { 'Authorization': `Bearer ${adminToken}`, 'Content-Type': 'application/json' };
    const payload = JSON.stringify({
        drawId: drawId,
        participantName: `Participant_${Math.random().toString(36).substring(7)}`,
        participantEmail: email
    });
    const res = http.post(`${CONFIG.drawCoreUrl}/api/tickets`, payload, { headers });

    if (res.status !== 201) {
        console.warn(`[WORKFLOW FAIL] Покупка билета не удалась. Status: ${res.status}, Body: ${res.body.substring(0, 150)}`);
        return false;
    }
    return true;
}

// =============================================================================
// DEFAULT: Сценарии нагрузки
// =============================================================================
export default function (data) {
    if (!data || data.adminTokens.length === 0 || !data.globalActiveDrawId) {
        console.error("[ERROR] Отсутствуют данные инициализации. Проверьте логи этапа SETUP.");
        sleep(1);
        return;
    }

    const randomIp = getRandomIp();
    const getRandomAdminToken = () => data.adminTokens[Math.floor(Math.random() * data.adminTokens.length)];
    const getRandomUserToken = () => data.userTokens.length > 0 ? data.userTokens[Math.floor(Math.random() * data.userTokens.length)] : data.adminTokens[0];

    const roll = Math.random();
    let logCounter = 0;

    function recordMetrics(res, expectedStatus, scenarioName) {
        const isExpected = res.status === expectedStatus;
        check(res, { [`${scenarioName}: строго статус ${expectedStatus}`]: (r) => isExpected });

        if (res.status >= 500) {
            server5xxErrors.add(1);
        } else if (isExpected) {
            p95Latency.add(res.timings.duration);
            if (scenarioName.includes('Complete Happy Path')) {
                successfulCompletions.add(1);
            }
        } else {
            if (logCounter < 5) {
                console.warn(`[DIAGNOSTIC] ${scenarioName} | Ожидалось: ${expectedStatus} | Получено: ${res.status} | Тело: ${res.body.substring(0, 150)}`);
                logCounter++;
            }
        }
    }

    // Сценарий 1 (25%): Полный успешный цикл создания и завершения розыгрыша (триггер Outbox)
    if (roll < 0.25) {
        const token = getRandomAdminToken();
        const drawId = createAndActivateDraw(token);

        if (drawId) {
            const email1 = `user1_${Date.now()}_${Math.random().toString(36)}@test.com`;
            const email2 = `user2_${Date.now()}_${Math.random().toString(36)}@test.com`;

            const ticket1Ok = buyTicketStrict(drawId, token, email1);
            const ticket2Ok = buyTicketStrict(drawId, token, email2);

            if (ticket1Ok && ticket2Ok) {
                const headers = { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json', 'X-Forwarded-For': randomIp };
                const res = http.post(`${CONFIG.drawCoreUrl}/api/draws/${drawId}/complete`, null, { headers });
                recordMetrics(res, 200, "Complete Happy Path (Outbox Trigger)");
            } else {
                console.warn(`[SKIP] Пропуск Complete Happy Path из-за неудачной покупки билетов для drawId: ${drawId}`);
            }
        }
    }
    // Сценарий 2 (15%): Негативный сценарий завершения розыгрыша (недостаточно билетов)
    else if (roll < 0.40) {
        const token = getRandomAdminToken();
        const drawId = createAndActivateDraw(token);

        if (drawId) {
            const email = `single_user_${Date.now()}_${Math.random().toString(36)}@test.com`;
            buyTicketStrict(drawId, token, email);

            const headers = { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' };
            const res = http.post(`${CONFIG.drawCoreUrl}/api/draws/${drawId}/complete`, null, { headers });
            recordMetrics(res, 400, "Complete Negative Path (< 2 tickets)");
        }
    }
    // Сценарий 3 (15%): Негативный сценарий отмены уже завершенного розыгрыша
    else if (roll < 0.55) {
        const token = getRandomAdminToken();
        const drawId = createAndActivateDraw(token);

        if (drawId) {
            const email1 = `cancel1_${Date.now()}_${Math.random().toString(36)}@test.com`;
            const email2 = `cancel2_${Date.now()}_${Math.random().toString(36)}@test.com`;

            const ticket1Ok = buyTicketStrict(drawId, token, email1);
            const ticket2Ok = buyTicketStrict(drawId, token, email2);

            if (ticket1Ok && ticket2Ok) {
                const headers = { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' };
                const completeRes = http.post(`${CONFIG.drawCoreUrl}/api/draws/${drawId}/complete`, null, { headers });

                if (completeRes.status === 200) {
                    const cancelRes = http.post(`${CONFIG.drawCoreUrl}/api/draws/${drawId}/cancel`, null, { headers });
                    recordMetrics(cancelRes, 400, "Cancel Negative Path (Already Completed)");
                } else {
                    console.warn(`[SKIP] Пропуск Cancel Negative Path, так как complete не удался (Status: ${completeRes.status})`);
                }
            }
        }
    }
    // Сценарий 4 (20%): Высокая конкуренция при покупке билетов в один розыгрыш
    else if (roll < 0.75) {
        const payload = JSON.stringify({
            drawId: data.globalActiveDrawId,
            participantName: `User_${Math.random().toString(36).substring(7)}`,
            participantEmail: `purchase_${Date.now()}_${Math.random().toString(36).substring(7)}@test.com`
        });
        const res = http.post(`${CONFIG.drawCoreUrl}/api/tickets`, payload, {
            headers: { 'Authorization': `Bearer ${getRandomAdminToken()}`, 'Content-Type': 'application/json', 'X-Forwarded-For': randomIp, 'X-Real-IP': randomIp }
        });

        const isOk = res.status === 201 || res.status === 400;
        check(res, { 'Buy Ticket: 201 or 400': (r) => isOk });
        if (res.status >= 500) server5xxErrors.add(1);
        else if (isOk) p95Latency.add(res.timings.duration);
    }
    // Сценарий 5 (15%): Операции чтения данных (роль ADMIN)
    else if (roll < 0.90) {
        const token = getRandomAdminToken();
        const headers = { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json', 'X-Forwarded-For': randomIp };

        const res = Math.random() > 0.5
            ? http.get(`${CONFIG.drawCoreUrl}/api/draws`, { headers })
            : http.get(`${CONFIG.drawCoreUrl}/api/tickets/draw/${data.globalActiveDrawId}`, { headers });

        recordMetrics(res, 200, "Read Operations");
    }
    // Сценарий 6 (10%): Проверки безопасности (отсутствие токена или недостаточные права)
    else {
        if (Math.random() > 0.5) {
            const res = http.get(`${CONFIG.drawCoreUrl}/api/draws`, {
                headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': randomIp }
            });
            recordMetrics(res, 401, "Security: No Token");
        } else {
            const payload = JSON.stringify({ name: 'Forbidden', description: 'Test', maxTickets: 10, ticketPrice: 10.0 });
            const res = http.post(`${CONFIG.drawCoreUrl}/api/draws`, payload, {
                headers: { 'Authorization': `Bearer ${getRandomUserToken()}`, 'Content-Type': 'application/json' }
            });
            recordMetrics(res, 403, "Security: Insufficient Rights");
        }
    }

    sleep(CONFIG.sleepDuration);
}