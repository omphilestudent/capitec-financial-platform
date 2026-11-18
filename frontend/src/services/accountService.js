import api from './api'

// account-service paths: /api/v1/accounts, /api/v1/accounts/{id},
// /api/v1/accounts/customer/{customerId}.

export const getAccountsByCustomer = async (customerId) => {
    const response = await api.get(`/accounts/customer/${customerId}`)
    return response.data
}

export const getAccountById = async (accountId) => {
    const response = await api.get(`/accounts/${accountId}`)
    return response.data
}

export const createAccount = async ({ customerId, accountType }) => {
    const response = await api.post('/accounts', { customerId, accountType })
    return response.data
}

// account-service does not expose update or close yet. These calls are
// kept for when it does; the UI keeps them disabled until then.
export const updateAccount = async (accountId, changes) => {
    const response = await api.put(`/accounts/${accountId}`, changes)
    return response.data
}

export const removeAccount = async (accountId) => {
    await api.delete(`/accounts/${accountId}`)
}
