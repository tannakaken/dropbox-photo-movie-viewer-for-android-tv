import { NextRequest, NextResponse } from 'next/server';
import { setFlowData } from "@/utils/api/redis";
import { generateSalt, generateSecureRandomString, hashToken } from '@/utils/api/security';
import { FlowRequest, FlowResponse } from '@/models/flow';
import { ErrorResponse } from '@/models/error';

export async function POST(request: NextRequest) {
    try {
        /**
         * CSRF（クロスサイトリクエストフォージェリ）対策のためのランダムなステート。
         * 
         * 認可フローのIDとしても使う。
         * 
         * UUID4は暗号学的に安全な乱数であることを仕様で保証していないので使わない。。このstateはoauth2のフローにおいてURLに添付される。
         * 
         */
        const state = generateSecureRandomString();
        /**
         * アクセスするさいに必要なトークン。これはhttpのヘッダに保存するので、httpsにすれば外に漏れる危険性は少ない。
         * 一時的なもの。
         */
        const tmpToken = generateSecureRandomString();
        const salt = generateSalt();
        const tmpTokenHash = hashToken(tmpToken, salt);
        const body = await request.json() as FlowRequest;
        /**
         * デバイスから送られてきたID
         * アプリ初回起動時に生成して送信する。
         */
        const deviceGenerateId = body.deviceGenerateId;

        if (!deviceGenerateId) {
             return NextResponse.json<ErrorResponse>({ error: 'Bad Request' }, { status: 400 });
        }

        await setFlowData(state, {tmpTokenHash, salt, deviceGenerateId, completed: false}, true);

        // Handle POST request
        return NextResponse.json<FlowResponse>({ state, tmpToken });
    } catch (error) {
        console.error(error);
        return NextResponse.json<ErrorResponse>({ error: 'Internal server error' }, { status: 500 });
    }
}
