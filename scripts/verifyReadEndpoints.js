require('dotenv').config({ quiet: true });
const { Op } = require('sequelize');
const sequelize = require('../models/index');
const User = require('../models/user');
const { findFirst } = require('../utils/modelQueries');

const baseUrl = (process.env.VERIFY_BASE_URL || 'http://127.0.0.1:3001').replace(/\/$/, '');
const authenticatedPaths = [
  '/auth/session',
  '/items',
  '/dashboard/summary',
  '/dashboard/stock-by-category',
  '/movements',
  '/categories',
  '/reports/stock-summary',
  '/reports/low-stock',
  '/reports/disposal',
  '/reports/category',
  '/reports/location',
  '/reports/stock-movement',
  '/disposals',
  '/profile',
  '/locations',
  '/notifications',
  '/notifications/unread-count',
];
const validationRequests = [
  ['POST', '/items', 400],
  ['PUT', '/items/0', 400],
  ['POST', '/items/0/image', 400],
  ['DELETE', '/items/0', 400],
  ['POST', '/categories', 400],
  ['PUT', '/categories/0', 400],
  ['DELETE', '/categories/0', 400],
  ['POST', '/locations', 400],
  ['PUT', '/locations/0', 400],
  ['DELETE', '/locations/0', 400],
  ['POST', '/movements', 400],
  ['POST', '/disposals', 400],
  ['PUT', '/disposals/0', 400],
  ['PUT', '/disposals/0/dispose', 400],
  ['PUT', '/notifications/0/read', 400],
  ['PUT', '/profile/change-password', 400],
  ['POST', '/profile/add-account', 400],
];

function describeBody(body) {
  if (Array.isArray(body)) return `array(${body.length})`;
  if (body && typeof body === 'object') return 'object';
  return typeof body;
}

async function request(path, token) {
  const response = await fetch(`${baseUrl}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const body = await response.json();
  console.log(`${response.status} ${path} ${describeBody(body)}`);

  if (!response.ok) {
    throw new Error(`${path} failed: ${body.message || response.statusText}`);
  }
}

async function requestValidation(method, path, expectedStatus, token) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: method === 'DELETE' ? undefined : '{}',
  });
  const body = await response.json();
  console.log(`${response.status} ${method} ${path} validation`);

  if (response.status !== expectedStatus) {
    throw new Error(
      `${method} ${path} returned ${response.status}, expected ${expectedStatus}: ` +
      `${body.message || response.statusText}`
    );
  }
}

async function main() {
  try {
    await request('/health');

    const user = await findFirst(User, {
      where: {
        token: { [Op.ne]: null },
        status: 'active',
        role: 'Admin IT',
      },
      attributes: ['token'],
    });

    if (!user?.token) {
      throw new Error('No active stored session token is available. Sign in once before verification.');
    }

    for (const path of authenticatedPaths) {
      await request(path, user.token);
    }

    for (const [method, path, expectedStatus] of validationRequests) {
      await requestValidation(method, path, expectedStatus, user.token);
    }

    console.log(
      `Verified ${authenticatedPaths.length + 1} read endpoints and ` +
      `${validationRequests.length} write validation paths successfully.`
    );
  } finally {
    await sequelize.close();
  }
}

main().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
