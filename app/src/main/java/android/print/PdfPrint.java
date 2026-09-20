package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;

public final class PdfPrint {
    public interface Callback {
        void onSuccess(File file);
        void onFailure(Exception error);
    }
    private final PrintAttributes attributes;
    public PdfPrint(PrintAttributes attributes) { this.attributes = attributes; }
    public void print(final PrintDocumentAdapter adapter, final File outputFile, final Callback callback) {
        try {
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("تعذر إنشاء مجلد PDF");
            if (outputFile.exists() && !outputFile.delete()) throw new IOException("تعذر استبدال ملف PDF السابق");
            adapter.onLayout(null, attributes, new CancellationSignal(), new PrintDocumentAdapter.LayoutResultCallback() {
                @Override public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                    try {
                        ParcelFileDescriptor destination = ParcelFileDescriptor.open(outputFile,
                            ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_READ_WRITE);
                        adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, destination, new CancellationSignal(),
                            new PrintDocumentAdapter.WriteResultCallback() {
                                @Override public void onWriteFinished(PageRange[] pages) { if (callback != null) callback.onSuccess(outputFile); }
                                @Override public void onWriteFailed(CharSequence error) { if (callback != null) callback.onFailure(new IOException(String.valueOf(error))); }
                                @Override public void onWriteCancelled() { if (callback != null) callback.onFailure(new IOException("تم إلغاء إنشاء PDF")); }
                            });
                    } catch (Exception e) { if (callback != null) callback.onFailure(e); }
                }
                @Override public void onLayoutFailed(CharSequence error) { if (callback != null) callback.onFailure(new IOException(String.valueOf(error))); }
                @Override public void onLayoutCancelled() { if (callback != null) callback.onFailure(new IOException("تم إلغاء تخطيط PDF")); }
            }, null);
        } catch (Exception e) { if (callback != null) callback.onFailure(e); }
    }
}