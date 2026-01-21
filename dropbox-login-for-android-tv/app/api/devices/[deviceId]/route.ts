import { AUTHORIZATION_HEADER_KEY, DEVICE_GENERATE_ID_HEADER_KEY } from '@/constants';
import { DropboxTokenResponse } from '@/models/dropbox_token';
import { deleteDeviceData, getDeviceData, isValidAccessToken } from '@/utils/api/redis';
import { NextRequest, NextResponse } from 'next/server';

const DROPBOX_TOKEN_ENDPOINT = "https://api.dropboxapi.com/oauth2/token";

export async function GET(
    request: NextRequest,
    context: { params: Promise<{deviceId: string}>}
) {
    try {
        const { deviceId } = await context.params;
        const authorization = request.headers.get(AUTHORIZATION_HEADER_KEY);
        const PREFIX = "Bearer ";
        if (!authorization?.startsWith(PREFIX)) {
            return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
        }
        const token = authorization.substring(PREFIX.length);
        if (!(await isValidAccessToken(token, deviceId))) {
            return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
        }
        const deviceData = await getDeviceData(deviceId);
        if (!deviceData) {
            return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
        }
        const deviceGenerateId = request.headers.get(DEVICE_GENERATE_ID_HEADER_KEY);
        if (deviceData.deviceGenerateId !== deviceGenerateId) {
            return NextResponse.json({ error: 'Bad Request' }, { status: 400 });
        }
        
        const dropboxRefreshToken = deviceData.dropboxRefreshToken
        
        const { NEXT_PUBLIC_DROPBOX_APP_KEY, DROPBOX_APP_SECRET } = process.env;
        const basicAuth = Buffer.from(
            `${NEXT_PUBLIC_DROPBOX_APP_KEY}:${DROPBOX_APP_SECRET}`
        ).toString("base64");

        const params = new URLSearchParams();
        params.append("grant_type", "refresh_token");
        params.append("refresh_token", dropboxRefreshToken);

        const response = await fetch(DROPBOX_TOKEN_ENDPOINT, {
            method: "POST",
            headers: {

                "Authorization": `Basic ${basicAuth}`,
                "Content-Type": "application/x-www-form-urlencoded",
            },
            body: params.toString(),
        });

        if (!response.ok) {
            const text = await response.text();
            return NextResponse.json({
                error: `Failed to refresh token: ${text}`
            }, { status: response.status});
        }

        const dropboxTokenResponse = await response.json() as DropboxTokenResponse;
        const dropboxAccessToken = dropboxTokenResponse.access_token;
        return NextResponse.json({ dropboxAccessToken });
    } catch (error) {
        console.error(error);
        return NextResponse.json({ error: 'Internal server error' }, { status: 500 });
    }
}


export async function DELETE(
    request: NextRequest,
    context: { params: Promise<{deviceId: string}>}
) {
    const { deviceId } = await context.params;
    const authorization = request.headers.get(AUTHORIZATION_HEADER_KEY);
    const PREFIX = "Bearer ";
    if (!authorization?.startsWith(PREFIX)) {
        return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }
    const token = authorization.substring(PREFIX.length);
    if (!(await isValidAccessToken(token, deviceId))) {
        return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }
    const deviceData = await getDeviceData(deviceId);
    if (!deviceData) {
        return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }
    const deviceGenerateId = request.headers.get(DEVICE_GENERATE_ID_HEADER_KEY);
    if (deviceData.deviceGenerateId !== deviceGenerateId) {
        return NextResponse.json({ error: 'Bad Request' }, { status: 400 });
    }
    deleteDeviceData(deviceId)
    return NextResponse.json({ ok: true });
}