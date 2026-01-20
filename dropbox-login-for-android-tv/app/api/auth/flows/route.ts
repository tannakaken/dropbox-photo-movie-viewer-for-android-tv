import { NextRequest, NextResponse } from 'next/server';
import { setFlowData } from "@/utils/api/redis";
import { generateSecureRandomString } from '@/utils/api/security';

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
        const token = generateSecureRandomString();
        const body = await request.json();
        /**
         * デバイスから送られてきたID
         * アプリ初回起動時に生成して送信する。
         */
        const deviceGenerateId = body["device_generate_id"];

        if (!deviceGenerateId) {
             return NextResponse.json({ error: 'Bad Request' }, { status: 400 });
        }

        await setFlowData(state, {token, deviceGenerateId, completed: false}, true);

        // Handle POST request
        return NextResponse.json({ state, token });
    } catch (error) {
        console.error(error);
        return NextResponse.json({ error: 'Internal server error' }, { status: 500 });
    }
}
