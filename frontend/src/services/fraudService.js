import api from './api'

export const searchFraud = async (params) => {
    const response = await api.get('/fraud', { params })
    return response.data
}

export const getFraud = async (fraudId) => {
    const response = await api.get(`/fraud/${fraudId}`)
    return response.data
}

export const getFraudForCase = async (caseId) => {
    const response = await api.get(`/fraud/case/${caseId}`)
    return response.data
}

export const updateFraudStatus = async (fraudId, investigationStatus) => {
    const response = await api.patch(`/fraud/${fraudId}/status`, { investigationStatus })
    return response.data
}
