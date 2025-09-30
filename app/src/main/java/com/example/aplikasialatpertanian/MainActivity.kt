package com.example.aplikasialatpertanian

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.storage.ktx.storage
import com.google.firebase.storage.storage

class MainActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private val storage = Firebase.storage

    // Deklarasi komponen UI
    private lateinit var etProductName: EditText
    private lateinit var etProductPrice: EditText
    private lateinit var etProductStock: EditText
    private lateinit var etProductDescription: EditText
    private lateinit var btnAddProduct: Button
    private lateinit var btnSelectImage: Button
    private lateinit var ivProductImage: ImageView
    private lateinit var tvImageStatus: TextView
    private lateinit var containerProducts: LinearLayout
    private lateinit var progressBar: ProgressBar

    private var selectedImageUri: Uri? = null
    private var isUploadingImage = false
    private var isEditing = false
    private var currentEditingId: String? = null

    // Register untuk permission launcher (cara modern)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission diberikan, buka gallery
            selectImageFromGallery()
        } else {
            // Permission ditolak
            if (shouldShowRequestPermissionRationale(getRequiredPermission())) {
                showPermissionExplanationDialog()
            } else {
                showGoToSettingsDialog()
            }
        }
    }

    // Register untuk image picker launcher
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                // Tampilkan gambar yang dipilih
                Glide.with(this)
                    .load(uri)
                    .centerCrop()
                    .into(ivProductImage)
                tvImageStatus.text = "Gambar siap diupload"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inisialisasi komponen UI
        initViews()

        // Setup button click listener
        setupButtonClickListener()

        // Load data dari Firestore
        loadProductsFromFirestore()
    }

    private fun initViews() {
        etProductName = findViewById(R.id.etProductName)
        etProductPrice = findViewById(R.id.etProductPrice)
        etProductStock = findViewById(R.id.etProductStock)
        etProductDescription = findViewById(R.id.etProductDescription)
        btnAddProduct = findViewById(R.id.btnAddProduct)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        ivProductImage = findViewById(R.id.ivProductImage)
        tvImageStatus = findViewById(R.id.tvImageStatus)
        containerProducts = findViewById(R.id.containerProducts)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupButtonClickListener() {
        btnSelectImage.setOnClickListener {
            checkPermissionAndSelectImage()
        }

        btnAddProduct.setOnClickListener {
            if (isUploadingImage) {
                Toast.makeText(this, "Sedang mengupload gambar...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val name = etProductName.text.toString().trim()
            val priceText = etProductPrice.text.toString().trim()
            val stockText = etProductStock.text.toString().trim()
            val description = etProductDescription.text.toString().trim()

            if (name.isEmpty() || priceText.isEmpty() || stockText.isEmpty()) {
                Toast.makeText(this, "Harap isi semua field!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val price = priceText.toDouble()
                val stock = stockText.toInt()

                if (isEditing) {
                    // Update produk yang sudah ada
                    updateProductInFirestore(currentEditingId!!, name, price, stock, description)
                } else {
                    // Tambah produk baru ke Firestore
                    addProductToFirestore(name, price, stock, description)
                }

            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Format harga atau stok tidak valid!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getRequiredPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ menggunakan READ_MEDIA_IMAGES
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // Android 12及以下 menggunakan READ_EXTERNAL_STORAGE
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun checkPermissionAndSelectImage() {
        val permission = getRequiredPermission()

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            // Permission sudah diberikan, buka gallery
            selectImageFromGallery()
        } else {
            // Permission belum diberikan, minta permission menggunakan cara modern
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Izin Akses Foto")
            .setMessage("Aplikasi membutuhkan akses ke foto dan media untuk memilih gambar produk. Data Anda aman dan hanya digunakan untuk menampilkan gambar produk.")
            .setPositiveButton("Izinkan") { dialog, which ->
                requestPermissionLauncher.launch(getRequiredPermission())
            }
            .setNegativeButton("Tolak") { dialog, which ->
                Toast.makeText(this, "Tidak dapat memilih gambar tanpa izin", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Izin Diperlukan")
            .setMessage("Izin akses foto diperlukan untuk memilih gambar. Silakan berikan izin melalui Settings > Permissions > Photos and videos.")
            .setPositiveButton("Buka Settings") { dialog, which ->
                openAppSettings()
            }
            .setNegativeButton("Nanti") { dialog, which ->
                Toast.makeText(this, "Fitur pemilihan gambar tidak tersedia", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun selectImageFromGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    private fun addProductToFirestore(name: String, price: Double, stock: Int, description: String) {
        progressBar.visibility = View.VISIBLE
        btnAddProduct.isEnabled = false

        if (selectedImageUri != null) {
            // Upload gambar terlebih dahulu
            uploadImageToFirebaseStorage { imageUrl ->
                saveProductToFirestore(name, price, stock, description, imageUrl)
            }
        } else {
            // Simpan produk tanpa gambar
            saveProductToFirestore(name, price, stock, description, "")
        }
    }

    private fun updateProductInFirestore(documentId: String, name: String, price: Double, stock: Int, description: String) {
        progressBar.visibility = View.VISIBLE
        btnAddProduct.isEnabled = false

        if (selectedImageUri != null) {
            // Upload gambar baru terlebih dahulu
            uploadImageToFirebaseStorage { imageUrl ->
                updateProductInFirestore(documentId, name, price, stock, description, imageUrl)
            }
        } else {
            // Update produk tanpa mengubah gambar
            updateProductInFirestore(documentId, name, price, stock, description, null)
        }
    }

    private fun updateProductInFirestore(documentId: String, name: String, price: Double, stock: Int, description: String, imageUrl: String?) {
        val updatedProduct = hashMapOf<String, Any>(
            "name" to name,
            "price" to price,
            "stock" to stock,
            "description" to description
        )

        // Jika ada imageUrl baru, tambahkan ke data yang diupdate
        imageUrl?.let {
            updatedProduct["imageUrl"] = it
        }

        db.collection("products")
            .document(documentId)
            .update(updatedProduct)
            .addOnSuccessListener {
                Log.d("Firestore", "Produk berhasil diupdate dengan ID: $documentId")
                Toast.makeText(this, "Produk $name berhasil diupdate!", Toast.LENGTH_SHORT).show()
                resetForm()
            }
            .addOnFailureListener { e ->
                Log.w("Firestore", "Gagal mengupdate produk", e)
                Toast.makeText(this, "Gagal mengupdate produk!", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                progressBar.visibility = View.GONE
                btnAddProduct.isEnabled = true
            }
    }

    private fun uploadImageToFirebaseStorage(onComplete: (String) -> Unit) {
        selectedImageUri?.let { uri ->
            isUploadingImage = true
            val fileName = "product_${System.currentTimeMillis()}.jpg"
            val storageRef = storage.reference.child("product_images/$fileName")

            // Tampilkan progress upload
            tvImageStatus.text = "Mengupload gambar..."

            storageRef.putFile(uri)
                .addOnSuccessListener { taskSnapshot ->
                    // Dapatkan download URL
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        isUploadingImage = false
                        tvImageStatus.text = "Gambar berhasil diupload"
                        onComplete(downloadUri.toString())
                    }
                }
                .addOnFailureListener { e ->
                    isUploadingImage = false
                    Log.w("FirebaseStorage", "Gagal upload gambar", e)
                    tvImageStatus.text = "Gagal upload gambar"
                    Toast.makeText(this, "Gagal upload gambar!", Toast.LENGTH_SHORT).show()
                    // Tetap simpan produk meski gambar gagal diupload
                    onComplete("")
                }
                .addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                    tvImageStatus.text = "Uploading: ${progress.toInt()}%"
                }
        } ?: run {
            onComplete("")
        }
    }

    private fun saveProductToFirestore(name: String, price: Double, stock: Int, description: String, imageUrl: String) {
        val newProduct = Product(
            name = name,
            price = price,
            stock = stock,
            description = description,
            imageUrl = imageUrl
        )

        db.collection("products")
            .add(newProduct)
            .addOnSuccessListener { documentReference ->
                Log.d("Firestore", "Produk berhasil ditambahkan dengan ID: ${documentReference.id}")
                Toast.makeText(this, "Produk $name berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                resetForm()
            }
            .addOnFailureListener { e ->
                Log.w("Firestore", "Gagal menambahkan produk", e)
                Toast.makeText(this, "Gagal menambahkan produk!", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                progressBar.visibility = View.GONE
                btnAddProduct.isEnabled = true
            }
    }

    private fun loadProductsFromFirestore() {
        progressBar.visibility = View.VISIBLE

        db.collection("products")
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    Log.w("Firestore", "Listen failed.", error)
                    Toast.makeText(this, "Gagal memuat data!", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    containerProducts.removeAllViews()

                    for (document in snapshot.documents) {
                        val product = document.toObject(Product::class.java)
                        product?.let {
                            addProductToView(it, document.id)
                        }
                    }
                } else {
                    containerProducts.removeAllViews()
                    val tvEmpty = TextView(this)
                    tvEmpty.text = "Belum ada produk. Tambahkan produk pertama!"
                    tvEmpty.textSize = 16f
                    tvEmpty.setPadding(0, 16, 0, 16)
                    containerProducts.addView(tvEmpty)
                }
            }
    }

    private fun addProductToView(product: Product, documentId: String) {
        val productItemView = LayoutInflater.from(this).inflate(R.layout.item_product, containerProducts, false)

        val tvName = productItemView.findViewById<TextView>(R.id.tvProductName)
        val tvPrice = productItemView.findViewById<TextView>(R.id.tvProductPrice)
        val tvStock = productItemView.findViewById<TextView>(R.id.tvProductStock)
        val tvDescription = productItemView.findViewById<TextView>(R.id.tvProductDescription)
        val ivProductImage = productItemView.findViewById<ImageView>(R.id.ivProductImage)
        val btnEdit = productItemView.findViewById<Button>(R.id.btnEdit)
        val btnDelete = productItemView.findViewById<Button>(R.id.btnDelete)

        tvName.text = product.name
        tvPrice.text = "Rp ${product.price}"
        tvStock.text = "Stok: ${product.stock}"
        tvDescription.text = product.description

        // Load gambar menggunakan Glide
        if (product.imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(product.imageUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_placeholder)
                .into(ivProductImage)
        } else {
            ivProductImage.setImageResource(R.drawable.ic_placeholder)
        }

        btnEdit.setOnClickListener {
            editProduct(documentId, product)
        }

        btnDelete.setOnClickListener {
            deleteProduct(documentId)
        }

        containerProducts.addView(productItemView)
    }

    private fun editProduct(documentId: String, product: Product) {
        // Set mode editing
        isEditing = true
        currentEditingId = documentId

        // Isi form dengan data produk yang akan diedit
        etProductName.setText(product.name)
        etProductPrice.setText(product.price.toString())
        etProductStock.setText(product.stock.toString())
        etProductDescription.setText(product.description)

        // Load gambar jika ada
        if (product.imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(product.imageUrl)
                .centerCrop()
                .into(ivProductImage)
            tvImageStatus.text = "Gambar produk saat ini"
        } else {
            ivProductImage.setImageResource(R.drawable.ic_add_photo)
            tvImageStatus.text = "Belum ada gambar yang dipilih"
        }

        // Ubah teks tombol menjadi "Update Produk"
        btnAddProduct.text = "Update Produk"

        // Scroll ke atas untuk melihat form
        findViewById<ScrollView>(R.id.scrollView).smoothScrollTo(0, 0)

        Toast.makeText(this, "Mengedit produk: ${product.name}", Toast.LENGTH_SHORT).show()
    }

    private fun deleteProduct(documentId: String) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Produk")
            .setMessage("Apakah Anda yakin ingin menghapus produk ini?")
            .setPositiveButton("Hapus") { dialog, which ->
                db.collection("products")
                    .document(documentId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Produk berhasil dihapus!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal menghapus produk!", Toast.LENGTH_SHORT).show()
                        Log.w("Firestore", "Error deleting document", e)
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun resetForm() {
        isEditing = false
        currentEditingId = null
        btnAddProduct.text = "Tambah Produk"
        clearInputFields()
    }

    private fun clearInputFields() {
        etProductName.text.clear()
        etProductPrice.text.clear()
        etProductStock.text.clear()
        etProductDescription.text.clear()
        selectedImageUri = null
        ivProductImage.setImageResource(R.drawable.ic_add_photo)
        tvImageStatus.text = "Belum ada gambar yang dipilih"
    }
}