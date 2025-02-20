package com.ujjwal.Stream_Backend.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OAuthConfig {

	@Value("${spotify.client.id}")
	private String spotifyClientId;

	@Value("${spotify.client.secret}")
	private String spotifyClientSecret;

	@Value("${spotify.redirect.uri}")
	private String spotifyRedirectUri;

	@Value("${spotify.auth.url}")
	private String spotifyAuthUrl;

	@Value("${spotify.token.url}")
	private String spotifyTokenUrl;

	@Value("${spotify.scope}")
	private String spotifyScope;

	@Value("${soundcloud.client.id}")
	private String soundcloudClientId;

	@Value("${soundcloud.client.secret}")
	private String soundcloudClientSecret;

	@Value("${soundcloud.redirect.uri}")
	private String soundcloudRedirectUri;

	@Value("${soundcloud.token.url}")
	private String soundcloudTokenUrl;

	@Value("${soundcloud.auth.url}")
	private String soundcloudAuthUrl;

	public String getSpotifyClientId() {
		return spotifyClientId;
	}

	public String getSpotifyClientSecret() {
		return spotifyClientSecret;
	}

	public String getSpotifyRedirectUri() {
		return spotifyRedirectUri;
	}

	public String getSpotifyAuthUrl() {
		return spotifyAuthUrl;
	}

	public String getSpotifyTokenUrl() {
		return spotifyTokenUrl;
	}

	public String getSpotifyScope() {
		return spotifyScope;
	}

	public String getSoundCloudClientId() {
		return soundcloudClientId;
	}

	public String getSoundCloudClientSecret() {
		return soundcloudClientSecret;
	}

	public String getSoundCloudRedirectUri() {
		return soundcloudRedirectUri;
	}

	public String getSoundcloudAuthUrl() {
		return soundcloudAuthUrl;
	}

	public String getSoundCloudTokenUrl() {
		return soundcloudTokenUrl;
	}
}
