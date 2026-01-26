package com.example.cachetothefuture.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cachetothefuture.data.OtherMetadata

enum class ChecksumState {
    Valid, Invalid, NA
}

@Composable
fun OtherCachedList(urls: List<OtherMetadata>, requestUrl: (String) -> Unit) {
    Column {
        Text("Others Cached Pages", style = MaterialTheme.typography.headlineMedium)
        Text("Here lists pages that others have cached.")
        CachedList(urls.map { Pair(it.url, ChecksumState.Valid) }, requestUrl)
    }
}

@Composable
fun HomeScreen(myViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val context = LocalContext.current
    val urls = myViewModel.listOfUrls.collectAsState()
    val urlNicknames = urls.value.map { metadata -> metadata.nickname }
    val otherUrls = myViewModel.otherCachedUrls.collectAsState()
    val requestedUrls = myViewModel.requests.collectAsState().value.map { request -> request.url }
    var powUpdates = myViewModel.powUpdates.collectAsState()
    val listState = rememberLazyListState()

    Log.d("Ross", urls.value.toString())
    Log.d("Ross", otherUrls.value.toString())

    val validated = urls.value.map { metadata ->
        val otherUrl = otherUrls.value.find { it.url == metadata.originalUrl }
        if (otherUrl != null) {
            return@map if (otherUrl.checksum == metadata.checksum) ChecksumState.Valid else ChecksumState.Invalid
        }
        return@map ChecksumState.Valid
    }

    Column() {
        MyCachedList(urlNicknames.zip(validated), { url ->
            myViewModel.openUrl(url, context)
        })
        OtherCachedList(otherUrls.value, { url -> myViewModel.requestUrl(url) })
        RequestedList(requestedUrls)
        InputURL(
            url = myViewModel.url.value,
            setURL = { url: String -> myViewModel.setURL(url) },
            addURL = { url: String ->
                myViewModel.addUrl(url, context)
                myViewModel.setURL("")
            },
        )
        Button(onClick = { myViewModel.bruteForceHash() }) {
            Text("Brute-Force Hash")
        }
        LazyColumn(state = listState) {
            items(powUpdates.value) { text ->
                Text(text)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
fun MyCachedListPreview() {
    MyCachedList(
        listOf(
            Pair("www.google.com", ChecksumState.Valid),
            Pair(
                "www.youtube.com",
                ChecksumState.Invalid
            )
        ), {})
}

@Composable
fun MyCachedList(listOfUrls: List<Pair<String, ChecksumState>>, openUrl: (String) -> Unit) {
    Column {
        Text("My Cached Pages", style = MaterialTheme.typography.headlineMedium)
        Text("Here you can load any pages you have previously cached.")
        CachedList(
            listOfUrls,
            { url -> openUrl(url) },
            modifier = Modifier.padding(top = 20.dp, bottom = 20.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
fun RequestedListPreview() {
    RequestedList(listOf("www.google.com", "rossevans.ca"))
}

@Composable
fun RequestedList(urls: List<String>) {
    Column {
        Text("My Requested Pages", style = MaterialTheme.typography.headlineMedium)
        if (urls.isEmpty()) {
            Text("You have not requested any pages yet.")
        } else {
            CachedList(
                urls.map { Pair(it, ChecksumState.NA) },
                {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
fun OtherCachedListPreview() {
    return OtherCachedList(
        listOf(
            OtherMetadata("rossevans.ca", ""),
            OtherMetadata("wikipedia.org/wiki/Canada", "")
        ), {})
}


@Composable
fun InputURL(
    url: String, setURL: (String) -> Unit, addURL: (String) -> Unit, modifier: Modifier = Modifier
) {
    Column {
        Text("Cache a page", style = MaterialTheme.typography.headlineMedium)
        TextField(
            value = url,
            onValueChange = setURL,
            label = { Text("Enter URL") },
            maxLines = 1,
            textStyle = TextStyle(fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addURL(url) })
        )
    }
}

@Preview
@Composable
fun PreviewCachedList() {
    CachedList(
        listOf(
            Pair("rossevans.ca", ChecksumState.Valid),
            Pair("uwaterloo.ca", ChecksumState.Invalid)
        ), {})
}

@Composable
fun CachedList(
    listOfUrls: List<Pair<String, ChecksumState>>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        for (url in listOfUrls) {
            Row {
                Text(text = url.first, modifier = Modifier.clickable { onClick(url.first) })
                when (url.second) {
                    ChecksumState.Valid -> Icon(Icons.Default.Check, "Checksum validated")
                    ChecksumState.Invalid -> Icon(Icons.Default.Close, "Checksum incorrect")
                    ChecksumState.NA -> Unit
                }
            }
        }
    }
}