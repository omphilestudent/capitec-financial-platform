import { createHmac, randomUUID } from 'node:crypto'

const secret = process.env.STATEMENT_JWT_SECRET
if (!secret || Buffer.byteLength(secret, 'utf8') < 32) {
  console.error('Set STATEMENT_JWT_SECRET (at least 32 bytes) in the environment.')
  process.exit(1)
}

const [, , role = 'CUSTOMER', customerId, ttlMinutes = '60'] = process.argv
if (!['CUSTOMER', 'STAFF'].includes(role)) {
  console.error('Role must be CUSTOMER or STAFF.')
  process.exit(1)
}
if (role === 'CUSTOMER' && !customerId) {
  console.error('Usage: issue-statement-token.mjs CUSTOMER <customerId> [ttlMinutes]')
  process.exit(1)
}

const b64url = (value) => Buffer.from(JSON.stringify(value)).toString('base64url')
const now = Math.floor(Date.now() / 1000)
const header = b64url({ alg: 'HS256', typ: 'JWT' })
const payload = b64url({
  sub: role === 'STAFF' ? 'staff-local-dev' : `customer-${customerId}`,
  roles: [role],
  ...(role === 'CUSTOMER' ? { customer_id: customerId } : {}),
  iss: process.env.STATEMENT_JWT_ISSUER || 'capitec-fin-platform',
  iat: now,
  exp: now + Number(ttlMinutes) * 60,
  jti: randomUUID(),
})
const signature = createHmac('sha256', secret).update(`${header}.${payload}`).digest('base64url')
process.stdout.write(`${header}.${payload}.${signature}\n`)
