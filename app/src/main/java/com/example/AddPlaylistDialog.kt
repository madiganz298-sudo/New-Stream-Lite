package com.example

import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.databinding.DialogAddPlaylistBinding
import com.google.android.material.tabs.TabLayout

class AddPlaylistDialog : DialogFragment() {

    interface AddPlaylistListener {
        fun onAddFromUrl(playlistName: String, url: String)
        fun onAddFromPaste(playlistName: String, rawContent: String)
        fun onBrowseFile()
        fun onAddFromFile(playlistName: String, uri: Uri)
    }

    private var _binding: DialogAddPlaylistBinding? = null
    private val binding get() = _binding!!
    
    private var listener: AddPlaylistListener? = null
    private var selectedFileUri: Uri? = null
    private var selectedFileName: String? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is AddPlaylistListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement AddPlaylistListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupListeners()
        
        // Restore file text state if selection happened while transaction was pending
        selectedFileName?.let { name ->
            binding.txtFileStatus.text = getString(R.string.file_selected_text, name)
            binding.txtFileStatus.setTextColor(resources.getColor(R.color.gold))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(android.view.Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun setupTabs() {
        binding.dialogTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.containerUrl.visibility = View.VISIBLE
                        binding.containerPaste.visibility = View.GONE
                        binding.containerFile.visibility = View.GONE
                    }
                    1 -> {
                        binding.containerUrl.visibility = View.GONE
                        binding.containerPaste.visibility = View.VISIBLE
                        binding.containerFile.visibility = View.GONE
                    }
                    2 -> {
                        binding.containerUrl.visibility = View.GONE
                        binding.containerPaste.visibility = View.GONE
                        binding.containerFile.visibility = View.VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupListeners() {
        binding.btnBrowseFile.setOnClickListener {
            listener?.onBrowseFile()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnAdd.setOnClickListener {
            val playlistName = binding.etPlaylistName.text?.toString()?.trim() ?: ""
            if (playlistName.isEmpty()) {
                binding.layoutPlaylistName.error = getString(R.string.playlist_name_required)
                return@setOnClickListener
            } else {
                binding.layoutPlaylistName.error = null
            }

            val currentTab = binding.dialogTabLayout.selectedTabPosition
            when (currentTab) {
                0 -> {
                    val url = binding.etPlaylistUrl.text?.toString()?.trim() ?: ""
                    if (url.isEmpty()) {
                        binding.layoutPlaylistUrl.error = "URL tidak boleh kosong"
                        return@setOnClickListener
                    }
                    listener?.onAddFromUrl(playlistName, url)
                    dismiss()
                }
                1 -> {
                    val body = binding.etPlaylistPaste.text?.toString()?.trim() ?: ""
                    if (body.isEmpty()) {
                        binding.layoutPlaylistPaste.error = "Konten tidak boleh kosong"
                        return@setOnClickListener
                    }
                    listener?.onAddFromPaste(playlistName, body)
                    dismiss()
                }
                2 -> {
                    val fileUri = selectedFileUri
                    if (fileUri == null) {
                        Toast.makeText(requireContext(), "Silakan pilih file M3U terlebih dahulu", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    listener?.onAddFromFile(playlistName, fileUri)
                    dismiss()
                }
            }
        }
    }

    fun onFileSelected(uri: Uri, name: String) {
        selectedFileUri = uri
        selectedFileName = name
        if (_binding != null) {
            binding.txtFileStatus.text = getString(R.string.file_selected_text, name)
            binding.txtFileStatus.setTextColor(resources.getColor(R.color.gold))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
