import api from './api'

// The gateway reports each downstream service's live /actuator/health result.
export const getServiceHealth = async () => {
    const response = await api.get('/services/health')
    return response.data
}
