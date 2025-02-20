package com.ujjwal.Stream_Backend.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ujjwal.Stream_Backend.Model.SpotifyOAuthModel;


public interface SpotifyTokenRepository extends JpaRepository<SpotifyOAuthModel,String> {
	Optional<SpotifyOAuthModel> findByUserId(String userId);
}
