package com.dev.ministudio;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ProjectListActivity extends AppCompatActivity {

    private ArrayList<String> projects = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#1E1E1E"));
        setContentView(R.layout.activity_project_list);

        ListView listView = findViewById(R.id.projectListView);
        FloatingActionButton fab = findViewById(R.id.fabAddProject);
        Toolbar toolbar = findViewById(R.id.toolbar);
        drawerLayout = findViewById(R.id.drawer_layout);

        setSupportActionBar(toolbar);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, android.R.string.ok, android.R.string.cancel);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // 1. ตรวจสอบสิทธิ์เข้าถึงไฟล์ระบบ
        checkPermissions();

        // 2. โหลดรายชื่อโปรเจกต์จากศูนย์กลางระบบ MiniStudio (/sdcard/MiniStudio)
        refreshProjectList();

        // 3. ตั้งค่า Adapter แสดงผลรายชื่อโปรเจกต์
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, projects) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = (TextView) view.findViewById(android.R.id.text1);
                text.setTextColor(android.graphics.Color.WHITE);
                text.setTextSize(18);
                text.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.sym_def_app_icon, 0, 0, 0);
                text.setCompoundDrawablePadding(30);
                return view;
            }
        };
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // ส่งค่าพิกัดพาร์ทศูนย์กลางไปให้ MainActivity เพื่อเปิดโปรเจกต์
                Intent intent = new Intent(ProjectListActivity.this, MainActivity.class);
                intent.putExtra("projectName", projects.get(position));
                startActivity(intent);
            }
        });

        // ระบบกดค้างเพื่อลบโปรเจกต์ตัวเกม
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            String projectName = projects.get(position);

            new AlertDialog.Builder(ProjectListActivity.this)
                .setTitle("ลบโปรเจกต์")
                .setMessage("คุณต้องการลบ " + projectName + " ใช่หรือไม่?")
                .setPositiveButton("ลบ", (dialog, which) -> {
                    // ย้ายพาร์ทลบไฟล์มาที่โฟลเดอร์ศูนย์กลาง (/sdcard/MiniStudio/)
                    File dir = new File("/sdcard/MiniStudio/" + projectName);
                    deleteRecursive(dir);

                    projects.remove(position);
                    adapter.notifyDataSetChanged();

                    Toast.makeText(ProjectListActivity.this, "ลบโปรเจกต์แล้ว", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("ยกเลิก", null)
                .show();

            return true;
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCreateProjectDialog();
            }
        });

        // 🌟 ระบบตรวจสอบการตั้งค่า GitHub อัตโนมัติ (แสดงครั้งแรกครั้งเดียว)
        SharedPreferences prefs = getSharedPreferences("GitHubPrefs", Context.MODE_PRIVATE);
        boolean isSetup = prefs.getBoolean("is_github_setup", false);
        
        if (!isSetup) {
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) {
                        showGitHubSettingsDialog();
                    }
                }
            }, 600); // หน่วงเวลา 0.6 วินาที
        }
    }

    private void refreshProjectList() {
        projects.clear();
        // ดึงรายชื่อตัวเกมจากโฟลเดอร์หลักศูนย์กลาง MiniStudio
        File root = new File("/sdcard/MiniStudio");
        if (!root.exists()) root.mkdirs();
        
        File[] files = root.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    projects.add(f.getName());
                }
            }
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            }
        }
    }

    // 🟢 หน้าต่างสร้างโปรเจกต์แบบ Advance เพิ่มตัวเลือก Language และ Minimum SDK (ดีไซน์พรีเมียมดาร์กโมด)
    private void showCreateProjectDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        // 1. คอนเทนเนอร์หลัก
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        int paddingPx = (int) (24 * getResources().getDisplayMetrics().density);
        mainLayout.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        mainLayout.setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"));

        // 2. แถวหัวข้อไดอะล็อกพร้อมไอคอน
        LinearLayout titleLayout = new LinearLayout(this);
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        titleLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        titleLayout.setPadding(0, 0, 0, (int) (6 * getResources().getDisplayMetrics().density));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("🚀 New Project");
        tvTitle.setTextColor(android.graphics.Color.WHITE);
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        titleLayout.addView(tvTitle);
        mainLayout.addView(titleLayout);

        // 3. คำอธิบายรายละเอียด
        TextView tvDesc = new TextView(this);
        tvDesc.setText("กำหนดค่าโครงสร้างและสภาพแวดล้อมสำหรับโปรเจกต์ใหม่ของคุณ");
        tvDesc.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
        tvDesc.setTextSize(13);
        tvDesc.setLineSpacing(0, 1.2f);
        tvDesc.setPadding(0, 0, 0, (int) (20 * getResources().getDisplayMetrics().density));
        mainLayout.addView(tvDesc);

        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxParams.bottomMargin = (int) (14 * getResources().getDisplayMetrics().density);

        // สไตล์สำหรับช่องกรอกและ Dropdown (สีเทาเข้ม ขอบมน เส้นขอบบาง)
        android.graphics.drawable.GradientDrawable inputStyle = new android.graphics.drawable.GradientDrawable();
        inputStyle.setColor(android.graphics.Color.parseColor("#252526"));
        inputStyle.setCornerRadius((int) (8 * getResources().getDisplayMetrics().density));
        inputStyle.setStroke((int) (1 * getResources().getDisplayMetrics().density), android.graphics.Color.parseColor("#3F3F46"));

        int inputPadding = (int) (12 * getResources().getDisplayMetrics().density);

        // --- ช่องกรอกที่ 1: Project Name ---
        TextView labelProjectName = new TextView(this);
        labelProjectName.setText("Project Name");
        labelProjectName.setTextColor(android.graphics.Color.parseColor("#D4D4D8"));
        labelProjectName.setTextSize(13);
        labelProjectName.setPadding(0, 0, 0, (int) (6 * getResources().getDisplayMetrics().density));
        mainLayout.addView(labelProjectName);

        final EditText etProjectName = new EditText(this);
        etProjectName.setHint("e.g., MyGame");
        etProjectName.setHintTextColor(android.graphics.Color.parseColor("#52525B"));
        etProjectName.setTextColor(android.graphics.Color.WHITE);
        etProjectName.setTextSize(14);
        etProjectName.setSingleLine(true);
        etProjectName.setBackground(inputStyle.getConstantState().newDrawable());
        etProjectName.setPadding(inputPadding, inputPadding, inputPadding, inputPadding);
        mainLayout.addView(etProjectName, boxParams);

        // --- ช่องกรอกที่ 2: Package Name ---
        TextView labelPackageName = new TextView(this);
        labelPackageName.setText("Package Name");
        labelPackageName.setTextColor(android.graphics.Color.parseColor("#D4D4D8"));
        labelPackageName.setTextSize(13);
        labelPackageName.setPadding(0, 0, 0, (int) (6 * getResources().getDisplayMetrics().density));
        mainLayout.addView(labelPackageName);

        final EditText etPackageName = new EditText(this);
        etPackageName.setHint("e.g., com.dev.mygame");
        etPackageName.setHintTextColor(android.graphics.Color.parseColor("#52525B"));
        etPackageName.setTextColor(android.graphics.Color.WHITE);
        etPackageName.setTextSize(14);
        etPackageName.setSingleLine(true);
        etPackageName.setBackground(inputStyle.getConstantState().newDrawable());
        etPackageName.setPadding(inputPadding, inputPadding, inputPadding, inputPadding);
        mainLayout.addView(etPackageName, boxParams);

        // ระบบแปลงชื่อ Package อัตโนมัติ
        etProjectName.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String name = s.toString().trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                if (!name.isEmpty()) {
                    etPackageName.setText("com.example." + name);
                } else {
                    etPackageName.setText("");
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // --- ช่องเลือกที่ 3: Language (Spinner) ---
        TextView labelLanguage = new TextView(this);
        labelLanguage.setText("Language");
        labelLanguage.setTextColor(android.graphics.Color.parseColor("#D4D4D8"));
        labelLanguage.setTextSize(13);
        labelLanguage.setPadding(0, 0, 0, (int) (6 * getResources().getDisplayMetrics().density));
        mainLayout.addView(labelLanguage);

        final android.widget.Spinner spinLanguage = new android.widget.Spinner(this);
        spinLanguage.setBackground(inputStyle.getConstantState().newDrawable());
        spinLanguage.setPadding(inputPadding, inputPadding, inputPadding, inputPadding);
        
        String[] languages = {"Java", "Kotlin"};
        ArrayAdapter<String> langAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, languages) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setTextSize(14);
                return tv;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setBackgroundColor(android.graphics.Color.parseColor("#252526"));
                tv.setPadding(30, 30, 30, 30);
                return tv;
            }
        };
        spinLanguage.setAdapter(langAdapter);
        mainLayout.addView(spinLanguage, boxParams);

        // --- ช่องเลือกที่ 4: Minimum SDK (Spinner) ---
        TextView labelMinSdk = new TextView(this);
        labelMinSdk.setText("Minimum SDK");
        labelMinSdk.setTextColor(android.graphics.Color.parseColor("#D4D4D8"));
        labelMinSdk.setTextSize(13);
        labelMinSdk.setPadding(0, 0, 0, (int) (6 * getResources().getDisplayMetrics().density));
        mainLayout.addView(labelMinSdk);

        final android.widget.Spinner spinMinSdk = new android.widget.Spinner(this);
        spinMinSdk.setBackground(inputStyle.getConstantState().newDrawable());
        spinMinSdk.setPadding(inputPadding, inputPadding, inputPadding, inputPadding);

        String[] sdkOptions = {
                "API 21: Android 5.0 (Lollipop)",
                "API 23: Android 6.0 (Marshmallow)",
                "API 26: Android 8.0 (Oreo)",
                "API 29: Android 10.0",
                "API 33: Android 13.0"
        };
        ArrayAdapter<String> sdkAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, sdkOptions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setTextSize(14);
                return tv;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setBackgroundColor(android.graphics.Color.parseColor("#252526"));
                tv.setPadding(30, 30, 30, 30);
                return tv;
            }
        };
        spinMinSdk.setAdapter(sdkAdapter);
        spinMinSdk.setSelection(1); // เลือก API 23 เป็นค่าเริ่มต้นตามสไตล์ IDE ทั่วไป
        mainLayout.addView(spinMinSdk, boxParams);


        final androidx.appcompat.app.AlertDialog dialog = builder.setView(mainLayout).create();

        // 4. แถบปุ่มกดด้านล่าง (CANCEL / CREATE)
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams btnLayoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLayoutParams.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        buttonLayout.setLayoutParams(btnLayoutParams);

        // ปุ่มยกเลิก
        android.widget.Button btnCancel = new android.widget.Button(this, null, 0, android.R.style.Widget_Material_Button_Borderless);
        btnCancel.setText("CANCEL");
        btnCancel.setTextColor(android.graphics.Color.parseColor("#A1A1AA"));
        btnCancel.setTextSize(14);
        btnCancel.setAllCaps(true);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        buttonLayout.addView(btnCancel);

        // ปุ่มสร้างโปรเจกต์
        android.widget.Button btnCreate = new android.widget.Button(this, null, 0, android.R.style.Widget_Material_Button_Borderless);
        btnCreate.setText("CREATE");
        btnCreate.setTextColor(android.graphics.Color.WHITE);
        btnCreate.setTextSize(14);
        btnCreate.setAllCaps(true);
        
        android.graphics.drawable.GradientDrawable createBtnBg = new android.graphics.drawable.GradientDrawable();
        createBtnBg.setColor(android.graphics.Color.parseColor("#248A3D"));
        createBtnBg.setCornerRadius((int) (6 * getResources().getDisplayMetrics().density));
        btnCreate.setBackground(createBtnBg);
        
        LinearLayout.LayoutParams createBtnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (40 * getResources().getDisplayMetrics().density));
        createBtnParams.leftMargin = (int) (12 * getResources().getDisplayMetrics().density);
        btnCreate.setLayoutParams(createBtnParams);
        btnCreate.setPadding((int) (20 * getResources().getDisplayMetrics().density), 0, (int) (20 * getResources().getDisplayMetrics().density), 0);    

        btnCreate.setOnClickListener(v -> {
            String name = etProjectName.getText().toString().trim();
            String packageName = etPackageName.getText().toString().trim();
            String selectedLang = spinLanguage.getSelectedItem().toString(); // "Java" หรือ "Kotlin"
            String selectedSdk = spinMinSdk.getSelectedItem().toString();   // "API 23: Android..."

            if (name.isEmpty()) {
                Toast.makeText(ProjectListActivity.this, "❌ กรุณากรอกชื่อโปรเจกต์", Toast.LENGTH_SHORT).show();
                return;
            }
            if (packageName.isEmpty() || !packageName.contains(".") || packageName.endsWith(".")) {
                Toast.makeText(ProjectListActivity.this, "❌ รูปแบบ Package Name ไม่ถูกต้อง", Toast.LENGTH_LONG).show();
                return;
            }

            // 🌟 ส่งค่าตัวแปร Language และ SDK ไปประมวลผลต่อที่ระบบสร้างโปรเจกต์หลังบ้าน
            createNewProject(name, packageName, selectedLang, selectedSdk);
            
            refreshProjectList(); 
            adapter.notifyDataSetChanged();
            Toast.makeText(ProjectListActivity.this, "Project Created (" + selectedLang + ")!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        buttonLayout.addView(btnCreate);
        mainLayout.addView(buttonLayout);

        if (dialog.getWindow() != null) {
            android.graphics.drawable.GradientDrawable dialogBg = new android.graphics.drawable.GradientDrawable();
            dialogBg.setColor(android.graphics.Color.parseColor("#1E1E1E"));
            dialogBg.setCornerRadius((int) (14 * getResources().getDisplayMetrics().density));
            dialog.getWindow().setBackgroundDrawable(dialogBg);
        }

        dialog.show();
    }

    // 🌟 เมธอดเวอร์ชันอัปเกรด: รับค่าตัวแปรภาษาและ SDK มาจำแนกเขียนโค้ดและสร้างโฟลเดอร์จริง
    private void createNewProject(String projectName, String packageName, String language, String minSdkVersionString) {
        String rootPath = "/sdcard/MiniStudio/" + projectName;
        
        // 1. นำค่าภาษามาสลับโฟลเดอร์ Source Code ให้ตรงตามไวยากรณ์ (src/main/java หรือ src/main/kotlin)
        String langFolder = language.toLowerCase(); // คืนค่าเป็น "java" หรือ "kotlin"
        String sourceDirPath = rootPath + "/app/src/main/" + langFolder + "/" + packageName.replace(".", "/");
        
        String[] folders = {
            sourceDirPath,
            rootPath + "/app/src/main/res/layout",
            rootPath + "/app/src/main/res/values"
        };

        for (String path : folders) {
            File f = new File(path);
            if (!f.exists()) f.mkdirs();
        }

        // 2. นำข้อความ SDK มาแกะเอาเฉพาะตัวเลข API ด้วย Regular Expression (เช่น "API 23: Android..." ดึงเฉพาะ "23")
        String minSdkDigits = minSdkVersionString.replaceAll("[^0-9]", "");
        if (minSdkDigits.length() > 2) {
            minSdkDigits = minSdkDigits.substring(0, 2); // ป้องกันกรณีดึงเลขเวอร์ชัน Android พ่วงมาด้วย
        }
        int minSdk = Integer.parseInt(minSdkDigits);

        // 3. สร้างไฟล์ AndroidManifest.xml
        String manifest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
            "    <application \n" +
            "        android:label=\"" + projectName + "\"\n" +
            "        android:theme=\"@style/AppTheme\">\n" + 
            "        <activity android:name=\".MainActivity\" android:exported=\"true\">\n" +
            "            <intent-filter>\n" +
            "                <action android:name=\"android.intent.action.MAIN\" />\n" +
            "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
            "            </intent-filter>\n" +
            "        </activity>\n" +
            "    </application>\n" +
            "</manifest>";
        writeFile(rootPath + "/app/src/main/AndroidManifest.xml", manifest);

        // 4. สร้างไฟล์ Layout แสดงผล (สลักตัวแปรภาษาลงบน TextView ให้เห็นชัดเจน)
        String layout = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"match_parent\"\n" +
            "    android:gravity=\"center\" \n" +
            "    android:orientation=\"vertical\">\n" +
            "    <TextView\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:text=\"Hello MiniStudio (" + language + ")!\" />\n" +
            "</LinearLayout>";
        writeFile(rootPath + "/app/src/main/res/layout/activity_main.xml", layout);

        String stringsXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n" +
            "    <string name=\"app_name\">" + projectName + "</string>\n</resources>";
        writeFile(rootPath + "/app/src/main/res/values/strings.xml", stringsXml);

        String stylesXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n" +
            "    <style name=\"AppTheme\" parent=\"Theme.MaterialComponents.DayNight.NoActionBar\">\n" +
            "    </style>\n</resources>";
        writeFile(rootPath + "/app/src/main/res/values/styles.xml", stylesXml);
        
        // 5. คัดแยกการเจนไฟล์ซอร์สโค้ดเริ่มต้นตามตัวแปรภาษาที่รับมา
        if ("Kotlin".equals(language)) {
            // เจนโค้ดรูปแบบภาษา Kotlin (.kt)
            String kotlinCode = "package " + packageName + "\n\n" +
                "import android.app.Activity\n" +
                "import android.os.Bundle\n" +
                "import " + packageName + ".R\n\n" + 
                "class MainActivity : Activity() {\n" +
                "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
                "        super.onCreate(savedInstanceState)\n" +
                "        setContentView(R.layout.activity_main)\n" +
                "    }\n" +
                "}";
            writeFile(sourceDirPath + "/MainActivity.kt", kotlinCode);
        } else {
            // เจนโค้ดรูปแบบภาษา Java (.java)
            String javaCode = "package " + packageName + ";\n\n" +
                "import android.app.Activity;\n" +
                "import android.os.Bundle;\n" +
                "import " + packageName + ".R;\n\n" + 
                "public class MainActivity extends Activity {\n" +
                "    @Override\n" +
                "    protected void onCreate(Bundle savedInstanceState) { \n" +
                "        super.onCreate(savedInstanceState);\n" +
                "        setContentView(R.layout.activity_main);\n" +
                "    }\n" +
                "}";
            writeFile(sourceDirPath + "/MainActivity.java", javaCode);
        }

        // 6. ส่งโครงสร้างและเตรียมค่าสำหรับส่งไปคอมไพล์บน GitHub CI/CD ต่อไป
        BuildEnvironmentManager envManager = new BuildEnvironmentManager(this);
        // ✅ แก้ไขส่งเป็น 5 ตัวแปร
        envManager.prepareGitHubWorkflow(rootPath, projectName, packageName, language, minSdk);

        
        // หมายเหตุเพิ่มเติม: หากใน BuildEnvironmentManager ของท่านถูกอัปเกรดให้เปลี่ยนค่าตามแปรผัน 
        // ท่านสามารถโยนตัวแปร `minSdk` หรือ `language` เสริมเข้าไปในอาร์กิวเมนต์คลาสนั้นได้เลยในอนาคตครับ
    }

    private void writeFile(String path, String content) {
        try {
            File file = new File(path);
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes("UTF-8"));
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_project_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_global_github_settings) {
            showGitHubSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showGitHubSettingsDialog() {
        SharedPreferences prefs = getSharedPreferences("GitHubPrefs", Context.MODE_PRIVATE);
        String savedUsername = prefs.getString("username", "");
        String savedEmail = prefs.getString("email", "");
        String savedToken = prefs.getString("token", "");

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        int paddingPx = (int) (24 * getResources().getDisplayMetrics().density);
        mainLayout.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        mainLayout.setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"));

        LinearLayout titleLayout = new LinearLayout(this);
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        titleLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        titleLayout.setPadding(0, 0, 0, (int) (6 * getResources().getDisplayMetrics().density));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("⚙️ ตั้งค่าบัญชี GitHub Sync");
        tvTitle.setTextColor(android.graphics.Color.WHITE);
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        titleLayout.addView(tvTitle);
        mainLayout.addView(titleLayout);

        TextView tvDesc = new TextView(this);
        tvDesc.setText("ข้อมูลนี้จะถูกบันทึกเพื่อใช้ส่งซอร์สโค้ดโปรเจกต์ขึ้นไปบิวด์บนคลาวด์อัตโนมัติ");
        tvDesc.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
        tvDesc.setTextSize(13);
        tvDesc.setLineSpacing(0, 1.2f);
        tvDesc.setPadding(0, 0, 0, (int) (20 * getResources().getDisplayMetrics().density));
        mainLayout.addView(tvDesc);

        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxParams.bottomMargin = (int) (14 * getResources().getDisplayMetrics().density);

        android.graphics.drawable.GradientDrawable inputStyle = new android.graphics.drawable.GradientDrawable();
        inputStyle.setColor(android.graphics.Color.parseColor("#252526"));
        inputStyle.setCornerRadius((int) (8 * getResources().getDisplayMetrics().density));
        inputStyle.setStroke((int) (1 * getResources().getDisplayMetrics().density), android.graphics.Color.parseColor("#3F3F46"));

        int inputPadding = (int) (12 * getResources().getDisplayMetrics().density);

        // --- GitHub Username ---
        TextView labelUsername = new TextView(this);
        labelUsername.setText("GitHub Username");
        labelUsername.setTextColor(android.graphics.Color.parseColor("#D4D4D8"));
        labelUsername.setTextSize(13);
        labelUsername.setPadding(0, 0, 0, (int) (6 * getResources().getDisplayMetrics().density));
        mainLayout.addView(labelUsername);

        final EditText etUsername = new EditText(this);
        etUsername.setHint("ระบุชื่อผู้ใช้ GitHub");
        etUsername.setHintTextColor(android.graphics.Color.parseColor("#52525B"));
        etUsername.setText(savedUsername);
        etUsername.setTextColor(android.graphics.Color.WHITE);
        etUsername.setTextSize(14);
        etUsername.setBackground(inputStyle.getConstantState().newDrawable());
        etUsername.setPadding(inputPadding, inputPadding, inputPadding, inputPadding);
        mainLayout.addView(etUsername, boxParams);

        // --- GitHub Email ---
        TextView labelEmail = new TextView(this);
        labelEmail.setText("GitHub Email");
        labelEmail.setTextColor(android.graphics.Color.parseColor("#D4D4D8"));
        labelEmail.setTextSize(13);
        labelEmail.setPadding(0, 0, 0, (int) (6 * getResources().getDisplayMetrics().density));
        mainLayout.addView(labelEmail);

        final EditText etEmail = new EditText(this);
        etEmail.setHint("ระบุอีเมลที่ผูกกับ GitHub");
        etEmail.setHintTextColor(android.graphics.Color.parseColor("#52525B"));
        etEmail.setText(savedEmail);
        etEmail.setTextColor(android.graphics.Color.WHITE);
        etEmail.setTextSize(14);
        etEmail.setBackground(inputStyle.getConstantState().newDrawable());
        etEmail.setPadding(inputPadding, inputPadding, inputPadding, inputPadding);
        mainLayout.addView(etEmail, boxParams);

        // --- Personal Access Token ---
        TextView labelToken = new TextView(this);
        labelToken.setText("Personal Access Token (Classic)");
        labelToken.setTextColor(android.graphics.Color.parseColor("#D4D4D8"));
        labelToken.setTextSize(13);
        labelToken.setPadding(0, 0, 0, (int) (6 * getResources().getDisplayMetrics().density));
        mainLayout.addView(labelToken);

        final EditText etToken = new EditText(this);
        etToken.setHint("วางโทเค็นสิทธิ์เข้าถึง (ghp_...)");
        etToken.setHintTextColor(android.graphics.Color.parseColor("#52525B"));
        etToken.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etToken.setText(savedToken);
        etToken.setTextColor(android.graphics.Color.WHITE);
        etToken.setTextSize(14);
        etToken.setBackground(inputStyle.getConstantState().newDrawable());
        etToken.setPadding(inputPadding, inputPadding, inputPadding, inputPadding);
        mainLayout.addView(etToken, boxParams);

        final androidx.appcompat.app.AlertDialog dialog = builder.setView(mainLayout).create();

        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams btnLayoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLayoutParams.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        buttonLayout.setLayoutParams(btnLayoutParams);

        android.widget.Button btnCancel = new android.widget.Button(this, null, 0, android.R.style.Widget_Material_Button_Borderless);
        btnCancel.setText("ยกเลิก");
        btnCancel.setTextColor(android.graphics.Color.parseColor("#A1A1AA"));
        btnCancel.setTextSize(14);
        btnCancel.setAllCaps(false);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        buttonLayout.addView(btnCancel);

        android.widget.Button btnSave = new android.widget.Button(this, null, 0, android.R.style.Widget_Material_Button_Borderless);
        btnSave.setText("บันทึกข้อมูล");
        btnSave.setTextColor(android.graphics.Color.WHITE);
        btnSave.setTextSize(14);
        btnSave.setAllCaps(false);
        
        android.graphics.drawable.GradientDrawable saveBtnBg = new android.graphics.drawable.GradientDrawable();
        saveBtnBg.setColor(android.graphics.Color.parseColor("#248A3D"));
        saveBtnBg.setCornerRadius((int) (6 * getResources().getDisplayMetrics().density));
        btnSave.setBackground(saveBtnBg);
        
        LinearLayout.LayoutParams saveBtnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (40 * getResources().getDisplayMetrics().density));
        saveBtnParams.leftMargin = (int) (12 * getResources().getDisplayMetrics().density);
        btnSave.setLayoutParams(saveBtnParams);
        btnSave.setPadding((int) (16 * getResources().getDisplayMetrics().density), 0, (int) (16 * getResources().getDisplayMetrics().density), 0);    
		
        btnSave.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String token = etToken.getText().toString().trim();

            if (username.isEmpty() || token.isEmpty()) {
                Toast.makeText(this, "❌ กรุณากรอก Username และ Token", Toast.LENGTH_LONG).show();
                return;
            }

            prefs.edit()
                .putString("username", username)
                .putString("email", email)
                .putString("token", token)
                .putBoolean("is_github_setup", true)
                .apply();

            Toast.makeText(this, "💾 บันทึกการตั้งค่าสำเร็จ", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        buttonLayout.addView(btnSave);
        mainLayout.addView(buttonLayout);

        if (dialog.getWindow() != null) {
            android.graphics.drawable.GradientDrawable dialogBg = new android.graphics.drawable.GradientDrawable();
            dialogBg.setColor(android.graphics.Color.parseColor("#1E1E1E"));
            dialogBg.setCornerRadius((int) (14 * getResources().getDisplayMetrics().density));
            dialog.getWindow().setBackgroundDrawable(dialogBg);
        }

        dialog.show();
    }
}
