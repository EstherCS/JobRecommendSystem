package external;

import java.io.BufferedReader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;

import entity.Item;
import entity.Item.ItemBuilder;

public class GitHubClient {
	// static final is constant, the value won't change
	// each request only different here, we can replace %s to what we want
	// lat long don't need to encryption, the values are number
	private static final String URL_TEMPLATE = "https://jobs.github.com/positions.json?description=%s&lat=%s&long=%s";
	// if user doesn't give us description, then use the default : developer
	// if we don't have default keyword still be fine, it's not necessary
	private static final String DEFAULT_KEYWORD = "developer";

	// send request to github, the send the return response to servlets
	public List<Item> search(double lat, double lon, String keyword) {
		// if user doesn't give us keyword, use the default one
		if (keyword == null) {
			keyword = DEFAULT_KEYWORD;
		}
		// try catch, avoid the exception occur
		try {
			// why need encode? if searching has blank, encode can change blank to +
			// UTF-8 is one of the mian way of encode, can deal with English and Chinese
			keyword = URLEncoder.encode(keyword, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		String url = String.format(URL_TEMPLATE, keyword, lat, lon);
		// send request to github API
		CloseableHttpClient httpClient = HttpClients.createDefault();
		try {
			// get the response
			CloseableHttpResponse response = httpClient.execute(new HttpGet(url));
			// analyze response, to see is success or fail
			// also can check is 400 or 500
			if (response.getStatusLine().getStatusCode() != 200) {
				return new ArrayList<>();
			}
			// if status is 200, then keep going
			// entity contains content, data ...
			HttpEntity entity = response.getEntity();
			// if entity is null, then return
			if (entity == null) {
				return new ArrayList<>();
			}
			// then analyze entity
			// entity.getContent() will get a stream of data, so use InputStreamReader to
			// read
			// but InputStreamReader can only read one by one, or need to set the read size,
			// but is not smart
			// if bigger then remain size will cause error ( 1001 = 500 + 500 + 1(error))
			// so we use BufferedReader to read line by line, is smarter
			BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
			// build response
			StringBuilder responseBody = new StringBuilder();
			// use variable line to read each line
			String line = null;
			while ((line = reader.readLine()) != null) {
				responseBody.append(line);
			}
			// transfer responseBody to java object( JSON array ), then return
			JSONArray array = new JSONArray(responseBody.toString());
			return getItemList(array);

		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new ArrayList<>();
	}

	private List<Item> getItemList(JSONArray array) {
		List<Item> itemList = new ArrayList<>();

		List<String> descriptionList = new ArrayList<>();

		for (int i = 0; i < array.length(); i++) {
			// We need to extract keywords from description since GitHub API
			// doesn't return keywords.
			String description = getStringFieldOrEmpty(array.getJSONObject(i), "description");
			if (description.equals("") || description.equals("\n")) {
				descriptionList.add(getStringFieldOrEmpty(array.getJSONObject(i), "title"));
			} else {
				descriptionList.add(description);
			}
		}

		// We need to get keywords from multiple text in one request since
		// MonkeyLearnAPI has limitations on request per minute.
		List<List<String>> keywords = MonkeyLearnClient
				.extractKeywords(descriptionList.toArray(new String[descriptionList.size()]));

		for (int i = 0; i < array.length(); ++i) {
			JSONObject object = array.getJSONObject(i);
			ItemBuilder builder = new ItemBuilder();

			builder.setItemId(getStringFieldOrEmpty(object, "id"));
			builder.setName(getStringFieldOrEmpty(object, "title"));
			builder.setAddress(getStringFieldOrEmpty(object, "location"));
			builder.setUrl(getStringFieldOrEmpty(object, "url"));
			builder.setImageUrl(getStringFieldOrEmpty(object, "company_logo"));

			builder.setKeywords(new HashSet<String>(keywords.get(i)));

			Item item = builder.build();
			itemList.add(item);
		}

		return itemList;
	}

	private String getStringFieldOrEmpty(JSONObject obj, String field) {
		return obj.isNull(field) ? "" : obj.getString(field);
	}

}
