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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_SMS = 1001;
    private static final String BANK_SENDER = "Bank-AlAhly";

    private final Calendar fromCalendar = Calendar.getInstance();
    private final Calendar toCalendar = Calendar.getInstance();

    private TextView tvFromDate;
    private TextView tvToDate;
    private TextView tvResultLabel;
    private TextView tvResultValue;

    private Button btnShowDeposits;
    private Button btnShowExpenses;
    private Button btnShowAvailable;

    private double lastExpenses = 0.0;
    private double lastDeposits = 0.0;
    private Double lastAvailableBalance = null;

    private final SimpleDateFormat uiDateFormat =
            new SimpleDateFormat("dd/MM/yyyy", new Locale("ar"));

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

        setupInsets();

        tvFromDate = findViewById(R.id.tvFromDate);
        tvToDate = findViewById(R.id.tvToDate);
        tvResultLabel = findViewById(R.id.tvResultLabel);
        tvResultValue = findViewById(R.id.tvResultValue);

        btnShowDeposits = findViewById(R.id.btnShowDeposits);
        btnShowExpenses = findViewById(R.id.btnShowExpenses);
        btnShowAvailable = findViewById(R.id.btnShowAvailable);

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

        btnShowDeposits.setOnClickListener(v -> showResult(ResultType.DEPOSITS));
        btnShowExpenses.setOnClickListener(v -> showResult(ResultType.EXPENSES));
        btnShowAvailable.setOnClickListener(v -> showResult(ResultType.AVAILABLE));

        if (!hasSmsPermission()) {
            requestSmsPermission();
        }
    }

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootContainer), (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    view.getPaddingLeft(),
                    16 + insets.top,
                    view.getPaddingRight(),
                    16 + insets.bottom
            );
            return windowInsets;
        });
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
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_SMS},
                REQ_SMS
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_SMS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                analyzeSms();
            } else {
                Toast.makeText(
                        this,
                        "لازم تسمح بقراءة الرسائل علشان التطبيق يشتغل",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private void analyzeSms() {
        if (fromCalendar.getTimeInMillis() > toCalendar.getTimeInMillis()) {
            Toast.makeText(
                    this,
                    "تاريخ البداية لازم يكون قبل تاريخ النهاية",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        double expenses = 0.0;
        double deposits = 0.0;
        Set<String> seenMessages = new HashSet<>();

        Uri uri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                ? Telephony.Sms.Inbox.CONTENT_URI
                : Uri.parse("content://sms/inbox");

        String[] projection = new String[]{"address", "body", "date"};
        String selection = "date >= ? AND date <= ?";
        String[] selectionArgs = new String[]{
                String.valueOf(fromCalendar.getTimeInMillis()),
                String.valueOf(toCalendar.getTimeInMillis())
        };

        try (Cursor cursor = getContentResolver().query(
                uri,
                projection,
                selection,
                selectionArgs,
                "date ASC"
        )) {
            if (cursor == null) {
                Toast.makeText(this, "لم أستطع قراءة الرسائل", Toast.LENGTH_LONG).show();
                return;
            }

            int addressIndex = cursor.getColumnIndex("address");
            int bodyIndex = cursor.getColumnIndex("body");
            int dateIndex = cursor.getColumnIndex("date");

            while (cursor.moveToNext()) {
                String address = addressIndex >= 0 ? cursor.getString(addressIndex) : "";
                String body = bodyIndex >= 0 ? cursor.getString(bodyIndex) : "";
                long date = dateIndex >= 0 ? cursor.getLong(dateIndex) : 0L;

                if (!isBankMessage(address, body)) {
                    continue;
                }

                String dedupeKey = buildMessageKey(address, body, date);
                if (!seenMessages.add(dedupeKey)) {
                    continue;
                }

                SmsType smsType = detectSmsType(body);
                double amount = extractAmount(body, smsType);

                if (amount <= 0) {
                    continue;
                }

                if (smsType == SmsType.EXPENSE) {
                    expenses += amount;
                } else if (smsType == SmsType.DEPOSIT) {
                    deposits += amount;
                }
            }

            lastExpenses = expenses;
            lastDeposits = deposits;
            lastAvailableBalance = findLatestAvailableBalance();

            showResult(ResultType.DEPOSITS);

        } catch (SecurityException ex) {
            Toast.makeText(
                    this,
                    "مفيش صلاحية كافية لقراءة الرسائل",
                    Toast.LENGTH_LONG
            ).show();
        } catch (Exception ex) {
            Toast.makeText(
                    this,
                    "حصل خطأ أثناء التحليل: " + ex.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private Double findLatestAvailableBalance() {
        Uri uri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                ? Telephony.Sms.Inbox.CONTENT_URI
                : Uri.parse("content://sms/inbox");

        String[] projection = new String[]{"address", "body", "date"};

        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, "date DESC")) {
            if (cursor == null) {
                return null;
            }

            int addressIndex = cursor.getColumnIndex("address");
            int bodyIndex = cursor.getColumnIndex("body");

            while (cursor.moveToNext()) {
                String address = addressIndex >= 0 ? cursor.getString(addressIndex) : "";
                String body = bodyIndex >= 0 ? cursor.getString(bodyIndex) : "";

                if (!isBankMessage(address, body)) {
                    continue;
                }

                Double available = extractAvailableAmount(body);
                if (available != null && available >= 0) {
                    return available;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private boolean isBankMessage(String address, String body) {
        String a = safeLower(address);
        String b = safeLower(body);

        boolean looksLikeBankSender = a.contains(BANK_SENDER.toLowerCase(Locale.ROOT));
        boolean mentionsBank = b.contains("البنك الأهلي");

        boolean cardExpenseMessage =
                b.contains("بطاقة الائتمان") && b.contains("تم خصم");

        boolean instantTransferDepositMessage =
                b.contains("بطاقتكم المنتهية") &&
                b.contains("تحويل لحظي") &&
                b.contains("بمبلغ");

        boolean cardPaymentMessage =
                b.contains("سداد مبلغ") &&
                (b.contains("بطاقة رقم") || b.contains("لبطاقة رقم"));

        return looksLikeBankSender
                || mentionsBank
                || cardExpenseMessage
                || instantTransferDepositMessage
                || cardPaymentMessage;
    }

    private SmsType detectSmsType(String body) {
        String normalized = normalizeArabicDigits(safeLower(body));

        if (normalized.matches(".*تم\\s*خصم\\s*[0-9]+(?:\\.[0-9]+)?\\s*جم.*")) {
            return SmsType.EXPENSE;
        }

        boolean instantTransferDeposit =
                normalized.contains("تحويل لحظي")
                        && normalized.contains("بطاقتكم المنتهية")
                        && normalized.contains("بمبلغ");

        boolean cardPaymentDeposit =
                normalized.contains("سداد مبلغ")
                        && (normalized.contains("بطاقة رقم") || normalized.contains("لبطاقة رقم"));

        if (instantTransferDeposit || cardPaymentDeposit) {
            return SmsType.DEPOSIT;
        }

        return SmsType.UNKNOWN;
    }

    private double extractAmount(String body, SmsType type) {
        if (TextUtils.isEmpty(body)) {
            return 0.0;
        }

        String normalized = normalizeArabicDigits(body).replace(",", "");

        Pattern expensePattern =
                Pattern.compile("تم\\s*خصم\\s*([0-9]+(?:\\.[0-9]+)?)\\s*جم");

        Pattern instantDepositPattern =
                Pattern.compile("بمبلغ\\s*([0-9]+(?:\\.[0-9]+)?)\\s*جم");

        Pattern repaymentPattern =
                Pattern.compile("سداد\\s*مبلغ\\s*([0-9]+(?:\\.[0-9]+)?)\\s*جم");

        Matcher m;

        if (type == SmsType.EXPENSE) {
            m = expensePattern.matcher(normalized);
            if (m.find()) {
                return parseDoubleSafely(m.group(1));
            }
        }

        if (type == SmsType.DEPOSIT) {
            m = repaymentPattern.matcher(normalized);
            if (m.find()) {
                return parseDoubleSafely(m.group(1));
            }

            m = instantDepositPattern.matcher(normalized);
            if (m.find()) {
                return parseDoubleSafely(m.group(1));
            }
        }

        return 0.0;
    }

    private Double extractAvailableAmount(String body) {
        if (TextUtils.isEmpty(body)) {
            return null;
        }

        String normalized = normalizeArabicDigits(body).replace(",", "");

        Pattern[] patterns = new Pattern[]{
                Pattern.compile("(?:المتاح|الحد\\s*المتاح|الرصيد\\s*المتاح)[^0-9]{0,20}([0-9]+(?:\\.[0-9]+)?)\\s*جم"),
                Pattern.compile("(?:اصبح|أصبح)[^0-9]{0,20}(?:المتاح|الحد\\s*المتاح|الرصيد\\s*المتاح)[^0-9]{0,20}([0-9]+(?:\\.[0-9]+)?)\\s*جم"),
                Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*جم[^\\n]{0,20}(?:متاح|المتاح)"),
                Pattern.compile("(?:available|balance)[^0-9]{0,20}([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(normalized);
            if (matcher.find()) {
                return parseDoubleSafely(matcher.group(1));
            }
        }

        return null;
    }

    private String buildMessageKey(String address, String body, long date) {
        String normalizedAddress = safeLower(address).trim();
        String normalizedBody = normalizeSpaces(normalizeArabicDigits(safeLower(body)));
        return normalizedAddress + "|" + date + "|" + normalizedBody;
    }

    private String normalizeSpaces(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("\\s+", " ").trim();
    }

    private double parseDoubleSafely(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String normalizeArabicDigits(String input) {
        if (input == null) {
            return "";
        }

        String result = input;
        result = result.replace('٠', '0').replace('١', '1').replace('٢', '2')
                .replace('٣', '3').replace('٤', '4').replace('٥', '5')
                .replace('٦', '6').replace('٧', '7').replace('٨', '8')
                .replace('٩', '9');

        result = result.replace('٫', '.').replace('٬', ',');

        return result;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private void resetResults() {
        lastExpenses = 0.0;
        lastDeposits = 0.0;
        lastAvailableBalance = null;
        showResult(ResultType.DEPOSITS);
    }

    private void showResult(ResultType resultType) {
        if (resultType == ResultType.DEPOSITS) {
            tvResultLabel.setText("إجمالي المبلغ اللي دخل للكارت");
            tvResultValue.setText(formatAmount(lastDeposits));
            tvResultValue.setTextColor(ContextCompat.getColor(this, R.color.depositColor));
        } else if (resultType == ResultType.EXPENSES) {
            tvResultLabel.setText("إجمالي المبلغ اللي اتصرف من الكارت");
            tvResultValue.setText(formatAmount(lastExpenses));
            tvResultValue.setTextColor(ContextCompat.getColor(this, R.color.expenseColor));
        } else {
            tvResultLabel.setText("المبلغ المتاح في الكارت حسب آخر رسالة من البنك");
            if (lastAvailableBalance == null) {
                tvResultValue.setText("غير متوفر");
            } else {
                tvResultValue.setText(formatAmount(lastAvailableBalance));
            }
            tvResultValue.setTextColor(ContextCompat.getColor(this, R.color.titleColor));
        }

        updateFilterButtons(resultType);
    }

    private void updateFilterButtons(ResultType selectedType) {
        styleResultButton(btnShowDeposits, selectedType == ResultType.DEPOSITS);
        styleResultButton(btnShowExpenses, selectedType == ResultType.EXPENSES);
        styleResultButton(btnShowAvailable, selectedType == ResultType.AVAILABLE);
    }

    private void styleResultButton(Button button, boolean selected) {
        int backgroundColor = ContextCompat.getColor(
                this,
                selected ? R.color.primary : R.color.buttonNeutral
        );
        int textColor = ContextCompat.getColor(
                this,
                selected ? android.R.color.white : R.color.titleColor
        );

        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(backgroundColor));
        button.setTextColor(textColor);
    }

    private String formatAmount(double amount) {
        return amountFormat.format(amount) + " جم";
    }

    private String formatAmount(Double amount) {
        if (amount == null) {
            return "غير متوفر";
        }
        return formatAmount(amount.doubleValue());
    }

    private enum SmsType {
        EXPENSE,
        DEPOSIT,
        UNKNOWN
    }

    private enum ResultType {
        DEPOSITS,
        EXPENSES,
        AVAILABLE
    }
}
