import { DEVICE_GENERATE_ID_HEADER_KEY } from '@/constants';
import { deleteRefreshToken, generateTokens, getDeviceData, isValidRefreshToken, refreshDeviceData } from '@/utils/api/redis';
import { NextRequest, NextResponse } from 'next/server';

type TokenRequest = {
    deviceId?: string;
    refreshToken?: string;
}

/**
 * リフレッシュトークンでトークンをリフレッシュする。
 * @param request 
 * @returns 
 */
export async function POST(request: NextRequest) {
    try {
        const {deviceId, refreshToken} = await request.json() as TokenRequest;
        
        if (!deviceId || !refreshToken) {
            return NextResponse.json({ error: 'Bad Request' }, { status: 400 });
        }
        if (!isValidRefreshToken(refreshToken, deviceId)) {
            return NextResponse.json({ error: 'Bad Request' }, { status: 400 });
        }
        const deviceData = await getDeviceData(deviceId);
        if (!deviceData) {
            return NextResponse.json({ error: 'Bad Request' }, { status: 400 });
        }
        const deviceGenerateId = request.headers.get(DEVICE_GENERATE_ID_HEADER_KEY);
        if (deviceData.deviceGenerateId !== deviceGenerateId) {
            return NextResponse.json({ error: 'Bad Request' }, { status: 400 });
        }
        const newTokens = generateTokens(deviceId);
        deleteRefreshToken(refreshToken);
        refreshDeviceData(deviceId);
        return NextResponse.json(newTokens);
    } catch (error) {
        console.error(error);
        return NextResponse.json({ error: 'Internal server error' }, { status: 500 });
    }
}
