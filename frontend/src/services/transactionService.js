import { openCustomerSession } from './statementService'
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

export const getTransactionForFraud = async (transactionId, customerId) => {
    try {
        return await getTransactionById(transactionId)
    } catch (err) {
        if (err.response?.status !== 404) throw err
        const token = await openCustomerSession(customerId)
        const response = await api.get(`/aggregation/transactions/${transactionId}`, {
            headers: { Authorization: `Bearer ${token}` },
        })
        const row = response.data
        return {
            id: row.id,
            accountId: row.accountId,
            transactionReference: row.externalId,
            type: row.direction,
            amount: row.amount,
            currency: row.currency,
            description: row.category,
            status: row.status,
            createdAt: row.occurredAt,
        }
    }
}
