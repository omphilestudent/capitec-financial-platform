import api from './api'

const filterParams = (filter = {}) => {
    const params = {}
    if (filter.from) params.from = filter.from
    if (filter.to) params.to = filter.to
    if (filter.type && filter.type !== 'ALL') params.type = filter.type
    return params
}

export const getRecentTransactions = async () => {
    const response = await api.get('/transactions')
    return response.data
}

export const getTransactionById = async (transactionId) => {
    const response = await api.get(`/transactions/${transactionId}`)
    return response.data
}

export const getTransactionsByAccount = async (accountId, filter) => {
    const response = await api.get(`/transactions/account/${accountId}`, { params: filterParams(filter) })
    return response.data
}

export const getTransactionSummary = async (accountId, filter) => {
    const response = await api.get(`/transactions/account/${accountId}/summary`, { params: filterParams(filter) })
    return response.data
}

export const createTransaction = async (transaction) => {
    const response = await api.post('/transactions', transaction)
    return response.data
}
