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
