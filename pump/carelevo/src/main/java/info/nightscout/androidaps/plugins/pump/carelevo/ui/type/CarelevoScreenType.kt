package info.nightscout.androidaps.plugins.pump.carelevo.ui.type

enum class CarelevoScreenType {
    CONNECTION_FLOW_START,    // 패치 연결 플로우 시작 화면
    COMMUNICATION_CHECK,      // 패치 점검 화면
    PATCH_DISCARD,            // 패치 종료 화면
    SAFETY_CHECK,             // 안전 점검 화면
    CANNULA_INSERTION,        // 바늘 주입
}