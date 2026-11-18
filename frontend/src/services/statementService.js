import api from './api'

const TOKEN_KEY = 'capitec.statementToken'

export const getStatementToken = () => {
    try {
        return sessionStorage.getItem(TOKEN_KEY) || ''
    } catch {
        return ''
    }
}

export const setStatementToken = (token) => {
    try {
        if (token) sessionStorage.setItem(TOKEN_KEY, token)
        else sessionStorage.removeItem(TOKEN_KEY)
    } catch {
        return
    }
}

const authHeaders = () => {
    const token = getStatementToken()
    return token ? { Authorization: `Bearer ${token}` } : {}
}

export const generateStatement = async (request) => {
    const response = await api.post('/statements/generate', request, { headers: authHeaders() })
    return response.data
}

export const listAccountStatements = async (accountId, page = 0, size = 10) => {
    const response = await api.get(`/statements/accounts/${accountId}`, {
        params: { page, size },
        headers: authHeaders(),
    })
    return response.data
}

export const getStatementEligibility = async (accountId, from, to) => {
    const response = await api.get('/statements/eligibility', {
        params: { accountId, from, to },
        headers: authHeaders(),
    })
    return response.data
}

export const createDownloadLink = async (statementId) => {
    const response = await api.post(`/statements/${statementId}/download-link`, null, { headers: authHeaders() })
    return response.data
}

export const revokeDownloadLinks = async (statementId) => {
    const response = await api.post(`/statements/${statementId}/revoke-links`, null, { headers: authHeaders() })
    return response.data
}

export const downloadStatementPdf = async (downloadToken) => {
    const response = await api.get(`/statements/download/${downloadToken}`, {
        headers: authHeaders(),
        responseType: 'blob',
    })
    return response.data
}
