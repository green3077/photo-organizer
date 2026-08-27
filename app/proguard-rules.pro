# WorkManager는 WorkSpec에 저장된 클래스 이름으로 reflection 인스턴스화하므로,
# R8이 이름을 바꾸거나(minify) 제거하면(shrink) 백그라운드 워커가 런타임에
# ClassNotFoundException으로 조용히 실패한다. Worker 서브클래스는 반드시 keep.
-keep class com.green3077.photoorganizer.notification.TrashPurgeWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# play-services-ads가 컴파일 시점 Android SDK보다 최신 API(API 35+)의 플랫폼 클래스를
# 조건부로 참조해 R8이 "missing class"로 잡아낸다. 실제 기기에서는 리플렉션으로 존재
# 여부를 확인하고 쓰므로 경고만 무시하면 된다(AGP가 생성한 missing_rules.txt 그대로).
-dontwarn android.media.LoudnessCodecController
-dontwarn android.media.LoudnessCodecController$OnLoudnessCodecUpdateListener
