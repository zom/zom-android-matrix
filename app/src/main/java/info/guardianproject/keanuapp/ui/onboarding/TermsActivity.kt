package info.guardianproject.keanuapp.ui.onboarding

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.guardianproject.keanuapp.R
import info.guardianproject.keanuapp.ui.BaseActivity
import org.matrix.androidsdk.rest.model.login.LocalizedFlowDataLoginTerms

class TermsActivity : BaseActivity() {

    companion object {
        const val EXTRA_TERMS = "TermsActivity.EXTRA_TERMS"
        const val REQUEST_CODE = 317
    }

    inner class TermsAdapter(private val terms: List<LocalizedFlowDataLoginTerms>?): RecyclerView.Adapter<TermsAdapter.ViewHolder>() {

        inner class ViewHolder(val webView: WebView): RecyclerView.ViewHolder(webView)

        @SuppressLint("SetJavaScriptEnabled")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val webView = WebView(parent.context)

            webView.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.MATCH_PARENT)

            webView.settings.apply {
                javaScriptEnabled = true

                useWideViewPort = true
                loadWithOverviewMode = true

                builtInZoomControls = true
                displayZoomControls = false
            }

            return ViewHolder(webView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.webView.loadUrl(terms?.get(position)?.localizedUrl)

            this@TermsActivity.title = terms?.get(position)?.localizedName
        }

        override fun getItemCount(): Int {
            return terms?.size ?: 0
        }
    }

    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mTermsLayoutManager: RecyclerView.LayoutManager
    private lateinit var mTermsAdapter: RecyclerView.Adapter<*>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)

        val terms = intent.getParcelableArrayListExtra<LocalizedFlowDataLoginTerms>(EXTRA_TERMS)

        mTermsLayoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        mTermsAdapter = TermsAdapter(terms)

        mRecyclerView = findViewById<RecyclerView>(R.id.recyclerView).apply {
            setHasFixedSize(true)

            layoutManager = mTermsLayoutManager

            adapter = mTermsAdapter
        }

        findViewById<Button>(R.id.btAccept).setOnClickListener {
            setResult(RESULT_OK, Intent())
            finish()
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_OK, Intent())
        finish()
    }
}