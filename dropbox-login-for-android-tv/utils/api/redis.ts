import { DeviceData } from '@/models/device';
import { FlowData } from '@/models/flow';
import { Redis } from '@upstash/redis';
import { generateSalt, generateSecureRandomString, hashToken, isValidToken } from './security';

const redisClient = new Redis({
    url: process.env.UPSTASH_REDIS_REST_URL!,
    token: process.env.UPSTASH_REDIS_REST_TOKEN!,
});

const constructFlowIdKey = (state: string): string => `android-tv-dropbox-flow-id-${state}`;
// ステートの製造時間は10分
const STATE_TTL_SECONDS = 600;

export const setFlowData = async (state: string, flowData: FlowData, setExpire = false) => {
    const flowIdKey = constructFlowIdKey(state);
    await redisClient.set(
        flowIdKey,
        flowData,
    );
    if (setExpire) {
        await redisClient.expire(flowIdKey, STATE_TTL_SECONDS);
    }
}

export const getFlowData = async (state: string) => {
    const flowIdKey = constructFlowIdKey(state);
    return await redisClient.get<FlowData>(
        flowIdKey
    );
}

export const deleteFlowData = async (state: string) => {
    const flowIdKey = constructFlowIdKey(state);
    await redisClient.del(flowIdKey);
}

const constructDeviceIdKey = (deviceId: string): string => `android-tv-dropbox-device-id-${deviceId}`;
/**
 * リフレッシュトークンの生存時間は約一か月の4週間 4 * 7 * 24 * 60 * 60 = 4 * 7 * 86400 = 4 * 604800 = 2419200
 * 
 * リフレッシュトークンはトークンのリフレッシュがあるたびに元のトークンは失効し、新しいものが再生成されて保存される。
 * 
 * デバイスIDの生存時間はこれより少しだけ長くして、リフレッシュトークンが失効した少し後に失効するようにする。
 * デバイスIDの生存時間はトークンのリフレッシュのたびに延長される
 * 
 */
const REFRESH_TOKEN_TTL_SECONDS = 2419200;

export const setDeviceData = async (deviceId: string, deviceData: DeviceData) => {
    const deviceIdKey = constructDeviceIdKey(deviceId);
    await redisClient.set(
        deviceIdKey,
        deviceData,
    );
    await redisClient.expire(deviceIdKey, REFRESH_TOKEN_TTL_SECONDS + 300);
}

export const getDeviceData = async (deviceId: string) => {
    const deviceIdKey = constructDeviceIdKey(deviceId);
    return await redisClient.get<DeviceData>(
        deviceIdKey
    );
}

export const deleteDeviceData = async (deviceId: string) => {
    const deviceIdKey = constructDeviceIdKey(deviceId);
    await redisClient.del(deviceIdKey);
}

export const refreshDeviceData = async (deviceId: string) => {
    const deviceIdKey = constructDeviceIdKey(deviceId);
    await redisClient.expire(deviceIdKey, REFRESH_TOKEN_TTL_SECONDS + 300);
}

const constructAccessTokenKey = (deviceId: string): string => `android-tv-dropbox-access-token-${deviceId}`;

type Token = {
    tokenHash: string;
    salt: string;
}

/**
 * アクセストークンの生存期間は1日24 * 60 * 60
 * 
 * これを使って、Dropboxのアクセストークンを手に入れる。
 */
const ACCSESS_TOKEN_TTL_SECONDS = 86400;
export const setAccessToken = async (accessToken: string, deviceId: string) => {
    const accessTokenKey = constructAccessTokenKey(deviceId);
    const salt = generateSalt();
    const tokenHash = hashToken(accessToken, salt);
    await redisClient.set(
        accessTokenKey,
        {
            tokenHash,
            salt,
        },
    );
    await redisClient.expire(accessTokenKey, ACCSESS_TOKEN_TTL_SECONDS);
}
export const isValidAccessToken = async (accessToken: string, deviceId: string) => {
    const accessTokenKey = constructAccessTokenKey(deviceId);
    const result = await redisClient.get<Token>(accessTokenKey);
    if (result) {
        return isValidToken(result.tokenHash, result.salt, accessToken);
    }
    return false;
}

const constructRefreshTokenKey = (deviceId: string): string => `android-tv-dropbox-refresh-token-${deviceId}`;
export const setRefreshToken = async (refreshToken: string, deviceId: string) => {
    const refreshTokenKey = constructRefreshTokenKey(deviceId);
    const salt = generateSalt();
    const tokenHash = hashToken(refreshToken, salt);
    await redisClient.set(
        refreshTokenKey,
        {
            tokenHash,
            salt,
        },
    );
    await redisClient.expire(refreshTokenKey, REFRESH_TOKEN_TTL_SECONDS);
}
export const isValidRefreshToken = async (refreshToken: string, deviceId: string) => {
    const refreshTokenKey = constructRefreshTokenKey(deviceId);
    const result = await redisClient.get<Token>(refreshTokenKey);
     if (result) {
        return isValidToken(result.tokenHash, result.salt, refreshToken);
    }
    return false;
}

export const generateTokens = async (deviceId: string) => {
    const accessToken = generateSecureRandomString();
    const refreshToken = generateSecureRandomString();

    await setAccessToken(accessToken, deviceId);
    await setRefreshToken(refreshToken, deviceId);

    return {
        accessToken,
        refreshToken
    };
}  
