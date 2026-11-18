import api from './api'

let sessionCache = { customerId: null, token: null, expiresAt: 0 }

export const openCustomerSession = async (customerId) => {
    const now = Date.now()
    if (sessionCache.customerId === customerId && sessionCache.token && now < sessionCache.expiresAt - 60000) {
        return sessionCache.token
    }
    const response = await api.post('/session', { customerId })
    sessionCache = {
        customerId,
        token: response.data.token,
        expiresAt: now + response.data.expiresInSeconds * 1000,
    }
    return sessionCache.token
}

const authHeaders = async (customerId) => ({ Authorization: `Bearer ${await openCustomerSession(customerId)}` })

export const getStatementEligibility = async (customerId, accountId, from, to) => {
    const response = await api.get('/statements/eligibility', {
        params: { accountId, from, to },
        headers: await authHeaders(customerId),
    })
    return response.data
}

export const generateStatement = async (customerId, request) => {
    const response = await api.post('/statements/generate', request, { headers: await authHeaders(customerId) })
    return response.data
}

export const listAccountStatements = async (customerId, accountId, page = 0, size = 20) => {
    const response = await api.get(`/statements/accounts/${accountId}`, {
        params: { page, size },
        headers: await authHeaders(customerId),
    })
    return response.data
}

export const createDownloadLink = async (customerId, statementId) => {
    const response = await api.post(`/statements/${statementId}/download-link`, null, {
        headers: await authHeaders(customerId),
    })
    return response.data
}

export const fetchStatementPdf = async (customerId, downloadToken) => {
    const response = await api.get(`/statements/download/${downloadToken}`, {
        headers: await authHeaders(customerId),
        responseType: 'blob',
    })
    return response.data
}
