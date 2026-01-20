import { AUTHORIZATION_HEADER_KEY, DEVICE_GENERATE_ID_HEADER_KEY } from '@/constants';
import { deleteFlowData, generateTokens, getFlowData, setDeviceData } from '@/utils/api/redis';
import { NextRequest, NextResponse } from 'next/server';
import {v4 as uuidv4} from 'uuid';

export async function GET(
    request: NextRequest,
    context: { params: Promise<{state: string}>}
) {
    try {
        const { state } = await context.params;
        const flowdata = await getFlowData(state);
        if (flowdata === null) {
            return NextResponse.json({ error: 'Not Found' }, { status: 404 });
        }
        const deviceGenerateId = request.headers.get(DEVICE_GENERATE_ID_HEADER_KEY);
        if (flowdata.deviceGenerateId !== deviceGenerateId) {
            return NextResponse.json({ error: 'Not Found' }, { status: 404 });
        }
        const authorization = request.headers.get(AUTHORIZATION_HEADER_KEY);
        const PREFIX = "Bearer ";
        if (!authorization?.startsWith(PREFIX)) {
            return NextResponse.json({ error: 'Not Found' }, { status: 404 });
        }
        const token = authorization.substring(PREFIX.length);
        if (token !== flowdata.token) {
            return NextResponse.json({ error: 'Not Found' }, { status: 404 });
        }
        if (!flowdata.completed) {
            return NextResponse.json({ completed: false });
        }
        const dropboxRefreshToken = flowdata.dropboxRefreshToken;
        const deviceId = uuidv4();
        
        setDeviceData(
            deviceId,
            {
                dropboxRefreshToken,
                deviceGenerateId
            }
        )
        deleteFlowData(state);
        const tokens = await generateTokens(deviceId);
        return NextResponse.json({ completed: true, deviceId, ...tokens });
    } catch (error) {
        console.error(error);
        return NextResponse.json({ error: 'Internal server error' }, { status: 500 });
    }
}

export async function DELETE(
    request: NextRequest,
    context: { params: Promise<{state: string}>}
) {
    const { state } = await context.params;
    const flowdata = await getFlowData(state);
    if (flowdata === null) {
        return NextResponse.json({ error: 'Not Found' }, { status: 404 });
    }
    const deviceGenerateId = request.headers.get(DEVICE_GENERATE_ID_HEADER_KEY);
    if (flowdata.deviceGenerateId !== deviceGenerateId) {
        return NextResponse.json({ error: 'Not Found' }, { status: 404 });
    }
    const authorization = request.headers.get(AUTHORIZATION_HEADER_KEY);
    const PREFIX = "Bearer ";
    if (!authorization?.startsWith(PREFIX)) {
        return NextResponse.json({ error: 'Not Found' }, { status: 404 });
    }
    const token = authorization.substring(PREFIX.length);
    if (token !== flowdata.token) {
        return NextResponse.json({ error: 'Not Found' }, { status: 404 });
    }
    deleteFlowData(state);
    return NextResponse.json({ ok: true });
}