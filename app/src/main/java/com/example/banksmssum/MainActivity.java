package com.example.banksmssum;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_SMS = 1001;
    private static final String BANK_SENDER = "Bank-AlAhly";

    private final Calendar fromCalendar = Calendar.getInstance();
    private final Calendar toCalendar = Calendar.getInstance();

    private TextView tvFromDate;
    private TextView tvToDate;
    private TextView tvExpenses;
    private TextView tvDeposits;
    private TextView tvNet;

    private final SimpleDateFormat uiDateFormat = new SimpleDateFormat("dd/MM/yyyy", new Locale("ar"));
    private final DecimalFormat amountFormat;

    public MainActivity() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        amountFormat = new DecimalFormat("#,##0.##", symbols);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        tvFromDate = findViewById(R.id.tvFromDate);
        tvToDate = findViewById(R.id.tvToDate);
        tvExpenses = findViewById(R.id.tvExpenses);
        tvDeposits = findViewById(R.id.tvDeposits);
        tvNet = findViewById(R.id.tvNet);

        Button btnPickFrom = findViewById(R.id.btnPickFrom);
        Button btnPickTo = findViewById(R.id.btnPickTo);
        Button btnAnalyze = findViewById(R.id.btnAnalyze);

        initDefaultDates();
        updateDateViews();
        resetResults();

        btnPickFrom.setOnClickListener(v -> showDatePicker(true));
        btnPickTo.setOnClickListener(v -> showDatePicker(false));
        btnAnalyze.setOnClickListener(v -> {
            if (hasSmsPermission()) {
                analyzeSms();
            } else {
                requestSmsPermission();
            }
        });

        if (!hasSmsPermission()) {
            requestSmsPermission();
        }
    }

    private void initDefaultDates() {
        toCalendar.set(Calendar.HOUR_OF_DAY, 23);
        toCalendar.set(Calendar.MINUTE, 59);
        toCalendar.set(Calendar.SECOND, 59);
        toCalendar.set(Calendar.MILLISECOND, 999);

        fromCalendar.set(Calendar.DAY_OF_MONTH, 1);
        fromCalendar.set(Calendar.HOUR_OF_DAY, 0);
        fromCalendar.set(Calendar.MINUTE, 0);
        fromCalendar.set(Calendar.SECOND, 0);
        fromCalendar.set(Calendar.MILLISECOND, 0);
    }

    private void showDatePicker(boolean isFrom) {
        Calendar target = isFrom ? fromCalendar : toCalendar;
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    target.set(Calendar.YEAR, year);
                    target.set(Calendar.MONTH, month);
                    target.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    if (isFrom) {
                        target.set(Calendar.HOUR_OF_DAY, 0);
                        target.set(Calendar.MINUTE, 0);
                        target.set(Calendar.SECOND, 0);
                        target.set(Calendar.MILLISECOND, 0);
                    } else {
                        target.set(Calendar.HOUR_OF_DAY, 23);
                        target.set(Calendar.MINUTE, 59);
                        target.set(Calendar.SECOND, 59);
                        target.set(Calendar.MILLISECOND, 999);
                    }

                    updateDateViews();
                },
                target.get(Calendar.YEAR),
                target.get(Calendar.MONTH),
                target.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void updateDateViews() {
        tvFromDate.setText(uiDateFormat.format(fromCalendar.getTime()));
        tvToDate.setText(uiDateFormat.format(toCalendar.getTime()));
    }

    private boolean hasSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_SMS}, REQ_SMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SMS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                analyzeSms();
            } else {
                Toast.makeText(this, "لازم تسمح بقراءة الرسائل علشان التطبيق يشتغل", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void analyzeSms() {
        if (fromCalendar.getTimeInMillis() > toCalendar.getTimeInMillis()) {
            Toast.makeText(this, "تاريخ البداية لازم يكون قبل تاريخ النهاية", Toast.LENGTH_LONG).show();
            return;
        }

        double expenses = 0.0;
        double deposits = 0.0;

        Uri uri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                ? Telephony.Sms.Inbox.CONTENT_URI
                : Uri.parse("content://sms/inbox");

        String[] projection = new String[]{"address", "body", "date"};
        String selection = "date >= ? AND date <= ?";
        String[] selectionArgs = new String[]{
                String.valueOf(fromCalendar.getTimeInMillis()),
                String.valueOf(toCalendar.getTimeInMillis())
        };

        try (Cursor cursor = getContentResolver().query(uri, projection, selection, selectionArgs, "date ASC")) {
            if (cursor == null) {
                Toast.makeText(this, "لم أستطع قراءة الرسائل", Toast.LENGTH_LONG).show();
                return;
            }

            int addressIndex = cursor.getColumnIndex("address");
            int bodyIndex = cursor.getColumnIndex("body");

            while (cursor.moveToNext()) {
                String address = addressIndex >= 0 ? cursor.getString(addressIndex) : "";
                String body = bodyIndex >= 0 ? cursor.getString(bodyIndex) : "";

                if (!isBankMessage(address, body)) continue;

                SmsType smsType = detectSmsType(body);
                double amount = extractAmount(body, smsType);

                if (amount <= 0) continue;

                if (smsType == SmsType.EXPENSE) {
                    expenses += amount;
                } else if (smsType == SmsType.DEPOSIT) {
                    deposits += amount;
                }
            }

            updateResults(expenses, deposits);
        } catch (SecurityException ex) {
            Toast.makeText(this, "مفيش صلاحية كافية لقراءة الرسائل", Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, "حصل خطأ أثناء التحليل: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean isBankMessage(String address, String body) {
        String a = safeLower(address);
        String b = safeLower(body);
        return a.contains(BANK_SENDER.toLowerCase(Locale.ROOT)) ||
                (b.contains("بطاقة الائتمان") && (b.contains("تم خصم") || b.contains("تم إضافة")));
    }

    private SmsType detectSmsType(String body) {
        String normalized = normalizeArabicDigits(safeLower(body));

        if (normalized.contains("تم خصم")) {
            return SmsType.EXPENSE;
        }

        if (normalized.contains("تم اضافة") || normalized.contains("تم إضافة")
                || normalized.contains("تحويل لحظي") || normalized.contains("بمبلغ")) {
            return SmsType.DEPOSIT;
        }

        return SmsType.UNKNOWN;
    }

    private double extractAmount(String body, SmsType type) {
        if (TextUtils.isEmpty(body)) return 0.0;

        String normalized = normalizeArabicDigits(body).replace(",", "");

        Pattern expensePattern = Pattern.compile("تم\\s*خصم\\s*([0-9]+(?:\\.[0-9]+)?)\\s*جم");
        Pattern depositPattern1 = Pattern.compile("بمبلغ\\s*([0-9]+(?:\\.[0-9]+)?)\\s*جم");
        Pattern depositPattern2 = Pattern.compile("تم\\s*إضافة\\s*([0-9]+(?:\\.[0-9]+)?)\\s*جم");
        Pattern genericAfterAction = Pattern.compile("(?:تم\\s*خصم|تم\\s*إضافة|بمبلغ)\\s*([0-9]+(?:\\.[0-9]+)?)");

        Matcher m;
        if (type == SmsType.EXPENSE) {
            m = expensePattern.matcher(normalized);
            if (m.find()) return parseDoubleSafely(m.group(1));
        } else if (type == SmsType.DEPOSIT) {
            m = depositPattern1.matcher(normalized);
            if (m.find()) return parseDoubleSafely(m.group(1));
            m = depositPattern2.matcher(normalized);
            if (m.find()) return parseDoubleSafely(m.group(1));
        }

        m = genericAfterAction.matcher(normalized);
        if (m.find()) return parseDoubleSafely(m.group(1));

        return 0.0;
    }

    private double parseDoubleSafely(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String normalizeArabicDigits(String input) {
        if (input == null) return "";
        String result = input;
        result = result.replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3').replace('٤', '4');
        result = result.replace('٥', '5').replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9');
        result = result.replace('٫', '.').replace('٬', ',');
        return result;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private void resetResults() {
        updateResults(0.0, 0.0);
    }

    private void updateResults(double expenses, double deposits) {
        double net = deposits - expenses;
        tvExpenses.setText(amountFormat.format(expenses) + " جم");
        tvDeposits.setText(amountFormat.format(deposits) + " جم");
        tvNet.setText(amountFormat.format(net) + " جم");
    }

    private enum SmsType {
        EXPENSE,
        DEPOSIT,
        UNKNOWN
    }
}
