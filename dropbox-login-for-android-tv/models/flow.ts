export type FlowData = {
    tmpTokenHash: string;
    salt: string;
    deviceGenerateId: string;
} & ({
    completed: false;
    dropboxRefreshToken?: undefined
} | {
    completed: true;
    dropboxRefreshToken: string;
});

export type FlowRequest = {
    deviceGenerateId?: string;
};

export type FlowResponse = {
    state: string;
    tmpToken: string;
};

export type FlowCheckResponse = {
    completed: false;
    deviceId?: undefined;
    accessToken?: undefined;
    refreshToken?: undefined
} | {
    completed: true;
    deviceId: string;
    accessToken: string;
    refreshToken: string;
};

