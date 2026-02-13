package com.wtfrepo.backend.arena.application.support;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configurable arena duel match profile parameters. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.arena.match")
public class ArenaMatchProperties {

  private String profileVersion = "v1.0.0";

  private String speciesDimensionKey = "species";

  private String diagnosisDimensionKey = "diagnosis";

  private int sameSpeciesScore = 100;

  private int adjacentSpeciesScore = 60;

  private int crossSpeciesScore = 0;

  private int diagnosisBaseScore = 15;

  private int diagnosisBonusCap = 30;

  private double cooldownAlpha = 0.3D;

  private int resetExcludeThreshold = 4;

  private List<String> adjacentSpeciesPairs = new ArrayList<>();
}