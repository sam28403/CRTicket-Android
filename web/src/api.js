import axios, { AxiosError } from 'axios';
import { native } from './android/platform.js';
import { createLocalApi } from './android/localApi.js';

const api = axios.create({
    baseURL: import.meta.env?.VITE_API_BASE_URL || '/api',
    headers: { 'X-CRTicket-Request': '1' },
    timeout: 5000,  // 请求超时
    withCredentials: true,
});

if (native) {
    const local = createLocalApi(localStorage);
    api.defaults.adapter = async config => {
        let path = config.url;
        const params = new URLSearchParams(config.params || {}).toString();
        if (params) path += (path.includes('?') ? '&' : '?') + params;
        let result;
        try {
            const isRailway = /^\/(train-info|left-ticket|llt-trains)(\/|\?|$)/.test(path);
            let data;
            if (isRailway) {
                const { requestRailway } = await import('./android/railway/api.js');
                data = await requestRailway(path, config.signal);
            } else {
                data = await local.request(config.method.toUpperCase(), path,
                    typeof config.data === 'string' ? JSON.parse(config.data) : config.data);
            }
            result = { status: 200, body: JSON.stringify(data) };
        } catch (error) {
            if (error.code === 'ERR_CANCELED' || error.code === 'ECONNABORTED') {
                throw new AxiosError(error.message, error.code, config);
            }
            result = { status: 503, body: JSON.stringify({ success: false, message: error.message }) };
        }
        let data;
        try { data = JSON.parse(result.body); }
        catch { data = { success: false, message: '铁路服务返回的数据格式异常，请稍后重试' }; result.status = 502; }
        const response = { data, status: result.status, statusText: '', headers: {}, config, request: null };
        if (result.status >= 200 && result.status < 300) return response;
        throw new AxiosError(data.message || '请求失败', AxiosError.ERR_BAD_RESPONSE, config, null, response);
    };
}

export default api;
