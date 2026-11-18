import api from './api'

export const getCases = async ({ status, priority, q, page = 0, size = 20 } = {}) => {
    const params = { page, size }
    if (status) params.status = status
    if (priority) params.priority = priority
    if (q) params.q = q
    const response = await api.get('/cases', { params })
    return response.data
}

export const getCase = async (caseId) => {
    const response = await api.get(`/cases/${caseId}`)
    return response.data
}

export const getCaseActivity = async (caseId) => {
    const response = await api.get(`/cases/${caseId}/activity`)
    return response.data
}

export const updateCase = async (caseId, changes) => {
    const response = await api.patch(`/cases/${caseId}`, changes)
    return response.data
}
