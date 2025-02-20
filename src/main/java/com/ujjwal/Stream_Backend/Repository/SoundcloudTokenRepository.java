package com.ujjwal.Stream_Backend.Repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ujjwal.Stream_Backend.Model.SoundcloudOAuthModel;

public interface SoundcloudTokenRepository extends JpaRepository<SoundcloudOAuthModel, Integer> {
	Optional<SoundcloudOAuthModel> findByUserId(Integer userId);
}
