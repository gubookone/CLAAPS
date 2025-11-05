package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch

import android.util.Log
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SafetyCheckResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SafetyCheckResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CarelevoPatchSafetyCheckUseCase @Inject constructor(
    private val patchObserver: CarelevoPatchObserver,
    private val patchRepository: CarelevoPatchRepository,
    private val patchInfoRepository: CarelevoPatchInfoRepository
) {

    /*fun execute() : Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                patchRepository.requestSafetyCheck()
                    .observeOn(Schedulers.io())
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request safety check is not pending")

                Log.d("connect_test", "[CarelevoRxPatchSafetyCheckUseCase] 1. 안전점검 요청")

                val requestSafetyCheckResult = patchObserver.patchEvent
                    .ofType<SafetyCheckResultModel>()
                    .blockingFirst()

                Log.d("connect_test", "[CarelevoRxPatchSafetyCheckUseCase] 2. 안전점검 요청 결과 수신 : $requestSafetyCheckResult")

                if(!(requestSafetyCheckResult.result == SafetyCheckResult.REP_REQUEST || requestSafetyCheckResult.result == SafetyCheckResult.REP_REQUEST1)) {
                    throw IllegalStateException("request safety check result is failed")
                }

                val currentThread = Thread.currentThread().name
                Log.d("connect_test", "[CarelevoRxPatchSafetyCheckUseCase] current thread : $currentThread")

                val ackResult = patchObserver.patchEvent
                    .ofType<SafetyCheckResultModel>()
                    .blockingFirst()

                Log.d("connect_test", "[CarelevoRxPatchSafetyCheckUseCase] 3. 안전점검 요청 결과 액크 수신 : $ackResult")

                if(ackResult.result != SafetyCheckResult.SUCCESS) {
                    throw IllegalStateException("safety check ack result is failed")
                }

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: throw NullPointerException("patch info must be not null")

                val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                    patchInfo.copy(checkSafety = true, updatedAt = DateTime.now())
                )
                if(!updatePatchInfoResult) {
                    throw IllegalStateException("update patch info is failed")
                }
                ResultSuccess
            }.fold(
                onSuccess = {
                    ResponseResult.Success(it as CarelevoUseCaseResponse)
                },
                onFailure = {
                    ResponseResult.Error(it)
                }
            )
        }.subscribeOn(Schedulers.io())
    }*/
    fun execute(): Single<ResponseResult<CarelevoUseCaseResponse>> {
        return patchRepository.requestSafetyCheck()
            .subscribeOn(Schedulers.io())
            .flatMap { req ->
                if (req !is RequestResult.Pending) {
                    return@flatMap Single.error(IllegalStateException("request safety check is not pending"))
                }

                Log.d("connect_test", "[UseCase] 1. 안전점검 요청 보냄")

                // 1) REQ/REQ1 기다리기 (짧은 기본 타임아웃)
                val requestReplySingle = patchObserver.patchEvent
                    .ofType(SafetyCheckResultModel::class.java)
                    .filter { it.result == SafetyCheckResult.REP_REQUEST || it.result == SafetyCheckResult.REP_REQUEST1 }
                    .firstOrError()
                    .timeout(100, TimeUnit.SECONDS) // 첫 응답은 보통 빨리 옴(필요 시 조정)

                requestReplySingle.flatMap { requestReply ->
                    Log.d("connect_test", "[UseCase] 2. 요청 결과 수신: $requestReply, ${requestReply.durationSeconds}")

                    val timeoutSec = (requestReply.durationSeconds + 30).toLong()
                    Log.d("connect_test", "[UseCase] ACK 타임아웃: ${timeoutSec}s")

                    // 2) ACK(SUCCESS) 기다리기 - durationSeconds 만큼
                    patchObserver.patchEvent
                        .ofType(SafetyCheckResultModel::class.java)
                        .filter { it.result == SafetyCheckResult.SUCCESS }
                        .firstOrError()
                        .timeout(timeoutSec, TimeUnit.SECONDS)
                }
            }
            .flatMap {
                Log.d("connect_test", "[UseCase] 3. ACK 수신: $it")

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: return@flatMap Single.error(NullPointerException("patch info must be not null"))

                val ok = patchInfoRepository.updatePatchInfo(
                    patchInfo.copy(checkSafety = true, updatedAt = DateTime.now())
                )
                if (!ok) return@flatMap Single.error(IllegalStateException("update patch info is failed"))

                Single.just<ResponseResult<CarelevoUseCaseResponse>>(
                    ResponseResult.Success(ResultSuccess as CarelevoUseCaseResponse)
                )
            }
            .onErrorReturn { e ->
                ResponseResult.Error(e)
            }
    }
}