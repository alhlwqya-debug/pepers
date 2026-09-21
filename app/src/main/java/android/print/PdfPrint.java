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

    public PdfPrint(PrintAttributes attributes) {
        this.attributes = attributes;
    }

    public void print(
            final PrintDocumentAdapter adapter,
            final File outputFile,
            final Callback callback
    ) {
        try {
            if (adapter == null) {
                throw new IllegalArgumentException("PrintDocumentAdapter غير موجود");
            }

            final File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("تعذر إنشاء مجلد PDF");
            }

            if (outputFile.exists() && !outputFile.delete()) {
                throw new IOException("تعذر استبدال ملف PDF السابق");
            }

            final CancellationSignal layoutSignal = new CancellationSignal();

            adapter.onLayout(
                    null,
                    attributes,
                    layoutSignal,
                    new PrintDocumentAdapter.LayoutResultCallback() {
                        @Override
                        public void onLayoutFinished(
                                PrintDocumentInfo info,
                                boolean changed
                        ) {
                            ParcelFileDescriptor destination = null;
                            try {
                                destination = ParcelFileDescriptor.open(
                                        outputFile,
                                        ParcelFileDescriptor.MODE_CREATE
                                                | ParcelFileDescriptor.MODE_TRUNCATE
                                                | ParcelFileDescriptor.MODE_READ_WRITE
                                );

                                final ParcelFileDescriptor finalDestination = destination;

                                adapter.onWrite(
                                        new PageRange[]{PageRange.ALL_PAGES},
                                        finalDestination,
                                        new CancellationSignal(),
                                        new PrintDocumentAdapter.WriteResultCallback() {
                                            private boolean completed = false;

                                            private void complete() {
                                                if (completed) return;
                                                completed = true;
                                                try {
                                                    finalDestination.close();
                                                } catch (IOException ignored) {
                                                }
                                                try {
                                                    adapter.onFinish();
                                                } catch (Exception ignored) {
                                                }
                                            }

                                            @Override
                                            public void onWriteFinished(PageRange[] pages) {
                                                complete();
                                                if (callback != null) {
                                                    callback.onSuccess(outputFile);
                                                }
                                            }

                                            @Override
                                            public void onWriteFailed(CharSequence error) {
                                                complete();
                                                if (callback != null) {
                                                    callback.onFailure(
                                                            new IOException(
                                                                    error == null
                                                                            ? "فشل إنشاء ملف PDF"
                                                                            : String.valueOf(error)
                                                            )
                                                    );
                                                }
                                            }

                                            @Override
                                            public void onWriteCancelled() {
                                                complete();
                                                if (callback != null) {
                                                    callback.onFailure(
                                                            new IOException("تم إلغاء إنشاء PDF")
                                                    );
                                                }
                                            }
                                        }
                                );
                            } catch (Exception e) {
                                if (destination != null) {
                                    try {
                                        destination.close();
                                    } catch (IOException ignored) {
                                    }
                                }
                                try {
                                    adapter.onFinish();
                                } catch (Exception ignored) {
                                }
                                if (callback != null) {
                                    callback.onFailure(e);
                                }
                            }
                        }

                        @Override
                        public void onLayoutFailed(CharSequence error) {
                            try {
                                adapter.onFinish();
                            } catch (Exception ignored) {
                            }
                            if (callback != null) {
                                callback.onFailure(
                                        new IOException(
                                                error == null
                                                        ? "فشل تخطيط ملف PDF"
                                                        : String.valueOf(error)
                                        )
                                );
                            }
                        }

                        @Override
                        public void onLayoutCancelled() {
                            try {
                                adapter.onFinish();
                            } catch (Exception ignored) {
                            }
                            if (callback != null) {
                                callback.onFailure(new IOException("تم إلغاء تخطيط PDF"));
                            }
                        }
                    },
                    null
            );
        } catch (Exception e) {
            try {
                adapter.onFinish();
            } catch (Exception ignored) {
            }
            if (callback != null) {
                callback.onFailure(e);
            }
        }
    }
}
