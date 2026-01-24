import { AUTHORIZATION_HEADER_KEY, DEVICE_GENERATE_ID_HEADER_KEY } from '@/constants';
import { ErrorResponse } from '@/models/error';
import { FlowCheckResponse } from '@/models/flow';
import { OkResponse } from '@/models/ok';
import { deleteFlowData, generateTokens, getFlowData, setDeviceData } from '@/utils/api/redis';
import { isValidToken } from '@/utils/api/security';
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
            return NextResponse.json<ErrorResponse>({ error: 'Not Found1' }, { status: 404 });
        }
        const deviceGenerateId = request.headers.get(DEVICE_GENERATE_ID_HEADER_KEY);
        if (flowdata.deviceGenerateId !== deviceGenerateId) {
            return NextResponse.json<ErrorResponse>({ error: `Not Found2` }, { status: 404 });
        }
        const authorization = request.headers.get(AUTHORIZATION_HEADER_KEY);
        const PREFIX = "Bearer ";
        if (!authorization?.startsWith(PREFIX)) {
            return NextResponse.json<ErrorResponse>({ error: 'Not Found3' }, { status: 404 });
        }
        const token = authorization.substring(PREFIX.length);
        if (!isValidToken(flowdata.tmpTokenHash, flowdata.salt, token)) {
            return NextResponse.json<ErrorResponse>({ error: 'Not Found4' }, { status: 404 });
        }
        if (!flowdata.completed) {
            return NextResponse.json<FlowCheckResponse>({ completed: false });
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
        return NextResponse.json<FlowCheckResponse>({ completed: true, deviceId, ...tokens });
    } catch (error) {
        console.error(error);
        return NextResponse.json<ErrorResponse>({ error: 'Internal server error' }, { status: 500 });
    }
}

export async function DELETE(
    request: NextRequest,
    context: { params: Promise<{state: string}>}
) {
    const { state } = await context.params;
    const flowdata = await getFlowData(state);
    if (flowdata === null) {
        return NextResponse.json<ErrorResponse>({ error: 'Not Found' }, { status: 404 });
    }
    const deviceGenerateId = request.headers.get(DEVICE_GENERATE_ID_HEADER_KEY);
    if (flowdata.deviceGenerateId !== deviceGenerateId) {
        return NextResponse.json<ErrorResponse>({ error: 'Not Found' }, { status: 404 });
    }
    const authorization = request.headers.get(AUTHORIZATION_HEADER_KEY);
    const PREFIX = "Bearer ";
    if (!authorization?.startsWith(PREFIX)) {
        return NextResponse.json<ErrorResponse>({ error: 'Not Found' }, { status: 404 });
    }
    const token = authorization.substring(PREFIX.length);
    if (!isValidToken(flowdata.tmpTokenHash, flowdata.salt, token)) {
        return NextResponse.json<ErrorResponse>({ error: 'Not Found' }, { status: 404 });
    }
    deleteFlowData(state);
    return NextResponse.json<OkResponse>({ ok: true });
}