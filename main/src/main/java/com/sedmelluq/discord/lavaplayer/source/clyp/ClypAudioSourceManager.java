package com.sedmelluq.discord.lavaplayer.source.clyp;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class ClypAudioSourceManager implements AudioSourceManager, HttpConfigurable {

    private static final String API_URL = "https://api.clyp.it";

    private static final String CHARSET = "UTF-8";
    private static final String TRACK_URL_REGEX = "^(?:http://|https://|)(?:www\\.|)clyp\\.it/([a-zA-Z0-9-_]+)$";

    private static final Pattern trackUrlPattern = Pattern.compile(TRACK_URL_REGEX);

    private HttpInterfaceManager httpInterfaceManager;

    public ClypAudioSourceManager() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();

    }

    @Override
    public String getSourceName() {
        return "clyp";
    }

    @Override
    public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
        return getTrack(reference);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {

    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new ClypAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {

    }

    private AudioTrack getTrack(AudioReference reference) {
        Matcher trackUrlMatcher = trackUrlPattern.matcher(reference.identifier); //i hope this returns the correct url?
        if(!trackUrlMatcher.matches()) {
            throw new FriendlyException("URL is invalid", FriendlyException.Severity.SUSPICIOUS, null);
        }

        try (CloseableHttpResponse response = getHttpInterface().execute(new HttpGet(API_URL + trackUrlMatcher.group(1)))) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 404) {
                throw new FriendlyException("That track does not exist.", COMMON, null);
            } else if (statusCode != 200) {
                throw new IOException("Invalid status code for video page response: " + statusCode);
            }

            JsonBrowser json = JsonBrowser.parse(IOUtils.toString(response.getEntity().getContent(), Charset.forName(CHARSET)));

            AudioTrackInfo trackInfo = new AudioTrackInfo(
                    json.get("Title").text(),
                    null, //Maybe throws NPE, using "Unknown" is probably better
                    json.get("duration").as(Integer.class),
                    null, //uwat ??
                    false,
                    json.get("Mp3Url").text()
            );

            return new ClypAudioTrack(trackInfo, this);

        } catch (IOException e) {
            throw new FriendlyException("Loading track from clyp failed.", SUSPICIOUS, e);
        }

    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }
}
