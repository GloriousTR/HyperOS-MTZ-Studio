package dev.glorioustr.mtzstudio.shevery;

interface IRootCommandService {
    int serviceUid();
    String execute(String command, int timeoutSeconds);
    void destroy();
}
