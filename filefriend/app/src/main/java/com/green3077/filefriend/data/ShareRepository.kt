package com.green3077.filefriend.data

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.InputStream

data class ShareInfo(
    val code: String,
    val fileName: String,
    val fileSize: Long,
    val downloadUrl: String,
    val storagePath: String,
    val expiresAtMillis: Long
)

sealed class FetchResult {
    data class Found(val share: ShareInfo) : FetchResult()
    object NotFound : FetchResult()
    object Expired : FetchResult()
}

/**
 * 3자리 코드 하나에 파일 한 건을 연결해 Firestore(메타데이터)+Storage(파일 본문)로
 * 중계한다. 코드는 숫자 3자리(000~999)뿐이라 동시에 여러 명이 코드를 발급받으면
 * 겹칠 수 있어, 트랜잭션으로 "예약"한 뒤에만 업로드를 시작한다.
 */
class ShareRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val sharesCollection = firestore.collection("shares")

    companion object {
        private const val RESERVATION_MILLIS = 5 * 60 * 1000L // 예약(업로드 중) 유효 시간: 5분
        const val SHARE_MILLIS = 30 * 60 * 1000L // 코드 발급 후 유효 시간: 30분
        private const val MAX_CODE_ATTEMPTS = 30
    }

    /**
     * Firestore/Storage 보안 규칙이 request.auth != null을 요구하므로, 회원가입 없이
     * 기기마다 익명 인증만 미리 해 둔다(로그인 화면 없이 앱 열자마자 바로 쓰게 하기 위함).
     */
    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    suspend fun uploadAndCreateCode(
        fileUri: Uri,
        fileName: String,
        fileSize: Long,
        onProgress: (Int) -> Unit
    ): ShareInfo {
        ensureSignedIn()
        val code = reserveCode()
        val storagePath = "shares/$code/$fileName"
        try {
            val storageRef = storage.reference.child(storagePath)
            val uploadTask = storageRef.putFile(fileUri)
            uploadTask.addOnProgressListener { snapshot ->
                val percent = if (snapshot.totalByteCount > 0) {
                    (snapshot.bytesTransferred * 100 / snapshot.totalByteCount).toInt()
                } else 0
                onProgress(percent)
            }
            uploadTask.await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            val expiresAt = System.currentTimeMillis() + SHARE_MILLIS
            val data = hashMapOf(
                "status" to "ready",
                "fileName" to fileName,
                "fileSize" to fileSize,
                "downloadUrl" to downloadUrl,
                "storagePath" to storagePath,
                "createdAt" to Timestamp.now(),
                "expiresAtMillis" to expiresAt
            )
            sharesCollection.document(code).set(data).await()
            return ShareInfo(code, fileName, fileSize, downloadUrl, storagePath, expiresAt)
        } catch (e: Exception) {
            runCatching { storage.reference.child(storagePath).delete().await() }
            runCatching { sharesCollection.document(code).delete().await() }
            throw e
        }
    }

    suspend fun cancelShare(share: ShareInfo) {
        runCatching { storage.reference.child(share.storagePath).delete().await() }
        runCatching { sharesCollection.document(share.code).delete().await() }
    }

    /** 코드로 공유 정보를 조회한다. */
    suspend fun fetchShare(code: String): FetchResult {
        ensureSignedIn()
        val doc = sharesCollection.document(code).get().await()
        if (!doc.exists() || doc.getString("status") != "ready") return FetchResult.NotFound

        val fileName = doc.getString("fileName") ?: return FetchResult.NotFound
        val fileSize = doc.getLong("fileSize") ?: 0L
        val downloadUrl = doc.getString("downloadUrl") ?: return FetchResult.NotFound
        val storagePath = doc.getString("storagePath") ?: return FetchResult.NotFound
        val expiresAtMillis = doc.getLong("expiresAtMillis") ?: 0L

        if (System.currentTimeMillis() > expiresAtMillis) {
            runCatching { storage.reference.child(storagePath).delete().await() }
            runCatching { sharesCollection.document(code).delete().await() }
            return FetchResult.Expired
        }
        return FetchResult.Found(ShareInfo(code, fileName, fileSize, downloadUrl, storagePath, expiresAtMillis))
    }

    /** Storage에서 파일 본문을 읽어올 스트림을 연다. */
    suspend fun openDownloadStream(storagePath: String): InputStream {
        ensureSignedIn()
        val snapshot = storage.reference.child(storagePath).stream.await()
        return snapshot.stream
    }

    /** 파일을 성공적으로 내려받은 뒤 호출해, 코드를 1회용으로 소멸시킨다. */
    suspend fun consumeShare(share: ShareInfo) {
        runCatching { storage.reference.child(share.storagePath).delete().await() }
        runCatching { sharesCollection.document(share.code).delete().await() }
    }

    private suspend fun reserveCode(): String {
        repeat(MAX_CODE_ATTEMPTS) {
            val candidate = (0..999).random().toString().padStart(3, '0')
            val docRef = sharesCollection.document(candidate)
            val reserved = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val expiresAtMillis = snapshot.getLong("expiresAtMillis") ?: 0L
                val isFree = !snapshot.exists() || System.currentTimeMillis() > expiresAtMillis
                if (isFree) {
                    transaction.set(
                        docRef,
                        hashMapOf(
                            "status" to "reserved",
                            "expiresAtMillis" to (System.currentTimeMillis() + RESERVATION_MILLIS)
                        )
                    )
                }
                isFree
            }.await()
            if (reserved) return candidate
        }
        throw IllegalStateException("사용 가능한 코드를 찾지 못했습니다. 잠시 후 다시 시도해주세요")
    }
}
