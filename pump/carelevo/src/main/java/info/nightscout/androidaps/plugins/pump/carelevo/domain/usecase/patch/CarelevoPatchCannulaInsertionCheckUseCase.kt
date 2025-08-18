package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch

import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.CannulaInsertionAckResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.CannulaInsertionResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultFailed
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoPatchCannulaInsertionCheckUseCase @Inject constructor(
    private val patchObserver : CarelevoPatchObserver,
    private val patchRepository : CarelevoPatchRepository,
    private val patchInfoRepository : CarelevoPatchInfoRepository
) {

    fun execute() : Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                patchRepository.requestCannulaInsertionCheck()
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request cannula insertion check is not pending")

                val requestCannulaInsertionResult = patchObserver.patchEvent
                    .ofType<CannulaInsertionResultModel>()
                    .blockingFirst()

                if(requestCannulaInsertionResult.result == Result.SUCCESS) {
                    patchRepository.requestConfirmCannulaInsertionCheck(true)
                        .blockingGet()
                        .takeIf { it is RequestResult.Pending }
                        ?: throw IllegalStateException("request confirm cannula insertion is not pending")

                    val requestConfirmCannulaInsertionResult = patchObserver.patchEvent
                        .ofType<CannulaInsertionAckResultModel>()
                        .blockingFirst()

                    if(requestConfirmCannulaInsertionResult.result == Result.SUCCESS) {
                        val patchInfo = patchInfoRepository.getPatchInfoBySync()
                            ?: throw NullPointerException("patch info must be not null")
                        val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                            patchInfo.copy(updatedAt = DateTime.now(), checkNeedle = true)
                        )
                        if(!updatePatchInfoResult) {
                            throw IllegalStateException("update patch info is failed")
                        }

                        ResultSuccess
                    } else {
                        val patchInfo = patchInfoRepository.getPatchInfoBySync()
                            ?: throw NullPointerException("patch info must be not null")
                        val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                            patchInfo.copy(updatedAt = DateTime.now(), checkNeedle = false)
                        )
                        if(!updatePatchInfoResult) {
                            throw IllegalStateException("update patch info is failed")
                        }
                        ResultFailed
                    }
                } else {
                    patchRepository.requestConfirmCannulaInsertionCheck(false)
                        .blockingGet()
                        .takeIf { it is RequestResult.Pending }
                        ?: throw IllegalStateException("request confirm cannula insertion is not pending")

                    val requestConfirmCannulaInsertionResult = patchObserver.patchEvent
                        .ofType<CannulaInsertionResultModel>()
                        .blockingFirst()

                    if(requestConfirmCannulaInsertionResult.result != Result.SUCCESS) {
                        throw IllegalStateException("request confirm cannula insertion result is failed")
                    }

                    val patchInfo = patchInfoRepository.getPatchInfoBySync()
                        ?: throw NullPointerException("patch info must be not null")
                    val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                        patchInfo.copy(updatedAt = DateTime.now(), checkNeedle = false)
                    )
                    if(!updatePatchInfoResult) {
                        throw IllegalStateException("update patch info is failed")
                    }
                    ResultFailed
                }
            }.fold(
                onSuccess = {
                    ResponseResult.Success(it)
                },
                onFailure = {
                    ResponseResult.Error(it)
                }
            )
        }.observeOn(Schedulers.io())
    }
}