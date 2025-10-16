package info.nightscout.androidaps.plugins.pump.carelevo.domain.type

enum class AlarmType(val code: Int) {
    WARNING(0),
    ALERT(1),
    NOTICE(2),
    UNKNOWN_TYPE(3);

    companion object {

        fun fromCode(code: Int?): AlarmType {
            return entries.find { it.code == code } ?: UNKNOWN_TYPE
        }

        fun fromAlarmType(type: AlarmType): Int {
            return type.code
        }
    }
}

enum class AlarmCause(val alarmType: AlarmType, val code: Int?, val value: Int? = null) {
    ALARM_WARNING_LOW_INSULIN(AlarmType.WARNING, 0x01),                                     // 인슐린 부족 경고
    ALARM_WARNING_PATCH_EXPIRED_PHASE_1(AlarmType.WARNING, 0x02),                           // 패치 사용 기간 만료
    ALARM_WARNING_LOW_BATTERY(AlarmType.WARNING, 0x03),                                     // 배터리 부족 경고
    ALARM_WARNING_INVALID_TEMPERATURE(AlarmType.WARNING, 0x04),                             // 부적합 온도(경고 사용 안함 -> 현재 ALERT 으로 통보)
    ALARM_WARNING_NOT_USED_APP_AUTO_OFF(AlarmType.WARNING, 0x05),                           // 주입 차단 경고 (앱 미사용 주입 차단)
    ALARM_WARNING_BLE_NOT_CONNECTED(AlarmType.WARNING, 0x06),                               // BLE 연결 안됨(패치 자체 원인값 -> 앱과 무관)
    ALARM_WARNING_INCOMPLETE_PATCH_SETTING(AlarmType.WARNING, 0x07),                        // 패치 적용 시간 종료 경고
    ALARM_WARNING_SELF_DIAGNOSIS_FAILED(AlarmType.WARNING, 0x09),                           // 안전점검 실패 경고
    ALARM_WARNING_PATCH_EXPIRED(AlarmType.WARNING, 0x0a),                                   // 연장된 패치 사용 기간 만료 경고
    ALARM_WARNING_PATCH_ERROR(AlarmType.WARNING, 0x0b),                                     // 패치 오류
    ALARM_WARNING_PUMP_CLOGGED(AlarmType.WARNING, 0x0c),                                    // 주입구 막힘
    ALARM_WARNING_NEEDLE_INSERTION_ERROR(AlarmType.WARNING, 99),                            // 바늘 삽입 오류

    ALARM_ALERT_OUT_OF_INSULIN(AlarmType.ALERT, 0x01),                                      // 인슐린 없음 주의
    ALARM_ALERT_PATCH_EXPIRED_PHASE_2(AlarmType.ALERT, 0x02),                               // 패치 사용 기간 만료 2차 주의
    ALARM_ALERT_LOW_BATTERY(AlarmType.ALERT, 0x03),                                         // 배터리 부족 주의
    ALARM_ALERT_INVALID_TEMPERATURE(AlarmType.ALERT, 0x04),                                 // 부적합 온도 주의
    ALARM_ALERT_APP_NO_USE(AlarmType.ALERT, 0x05),                                          // 앱 미 사용 타이머 종료 주의
    ALARM_ALERT_BLE_NOT_CONNECTED(AlarmType.ALERT, 0x06),                                   // BLE 연결 안됨(패치 자체 원인값 -> 앱과 무관)
    ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE(AlarmType.ALERT, 0x07),                        // 패치 연결 미완료 주의
    ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT(AlarmType.ALERT, 0x08),                     // 기저 주입 재개 시간 종료 주의
    ALARM_ALERT_PATCH_EXPIRED_PHASE_1(AlarmType.ALERT, 0x0a),                               // 패치 사용 기간 만료 1차 주의
    ALARM_ALERT_BLUETOOTH_OFF(AlarmType.ALERT, 97),                                         // 블루투스 꺼짐 주의

    ALARM_NOTICE_LOW_INSULIN(AlarmType.NOTICE, 0x01),                                       // 인슐린 잔여량 임계치 도달
    ALARM_NOTICE_PATCH_EXPIRED(AlarmType.NOTICE, 0x02),                                     // 패치 사용 기간 만료 알림
    ALARM_NOTICE_ATTACH_PATCH_CHECK(AlarmType.NOTICE, 0x09),                                // 부착 부위 확인 알림
    ALARM_NOTICE_TIME_ZONE_CHANGED(AlarmType.NOTICE, 96),                                   // 시간대 변경 알림
    ALARM_NOTICE_BG_CHECK(AlarmType.NOTICE, 98),                                            // 혈당 확인 알림
    ALARM_NOTICE_LGS_START(AlarmType.NOTICE, 99),                                           // LGS 작동 시작
    ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM(AlarmType.NOTICE, 100, 1),    // LGS 작동 종료
    ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS(AlarmType.NOTICE, 100, 2),                    // LGS 작동 종료
    ALARM_NOTICE_LGS_FINISHED_TIME_OVER(AlarmType.NOTICE, 100, 3),                    // LGS 작동 종료
    ALARM_NOTICE_LGS_FINISHED_OFF_LGS(AlarmType.NOTICE, 100, 4),                      // LGS 작동 종료
    ALARM_NOTICE_LGS_FINISHED_HIGH_BG(AlarmType.NOTICE, 100, 5),                      // LGS 작동 종료
    ALARM_NOTICE_LGS_FINISHED_UNKNOWN(AlarmType.NOTICE, 100),                               // LGS 작동 종료
    ALARM_NOTICE_LGS_NOT_WORKING(AlarmType.NOTICE, 101),                                    // LGS 작동 불가

    ALARM_UNKNOWN(AlarmType.UNKNOWN_TYPE, null);                                            // 알수 없는 알림

    companion object {

        fun fromTypeAndCode(alarmType: AlarmType, code: Int?, value: Int? = null): AlarmCause {
            return entries.find {
                it.alarmType == alarmType && it.code == code && it.value == value
            } ?: ALARM_UNKNOWN
        }
    }
}