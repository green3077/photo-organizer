# WorkManager는 WorkSpec에 저장된 클래스 이름으로 reflection 인스턴스화하므로,
# R8이 이름을 바꾸거나(minify) 제거하면(shrink) 백그라운드 워커가 런타임에
# ClassNotFoundException으로 조용히 실패한다. Worker 서브클래스는 반드시 keep.
-keep class com.green3077.photoorganizer.notification.TrashPurgeWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
