package com.kelsos.mbrc.data.library;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

@Table(name = "artist", database = Cache.class)
public class Artist extends BaseModel {

  @JsonIgnore
  @Column
  @PrimaryKey(autoincrement = true)
  private long id;
  @JsonProperty("artist")
  @Column
  private String artist;
  @JsonProperty("count")
  @Column
  private int count;

  public Artist() {

  }

  public Artist(JsonNode node) {
    this.artist = node.path("artist").textValue();
    this.count = node.path("count").intValue();
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  @JsonProperty("artist")
  public String getArtist() {
    return artist;
  }

  @JsonProperty("artist")
  public void setArtist(String artist) {
    this.artist = artist;
  }

  @SuppressWarnings("unused")
  @JsonProperty("count")
  public int getCount() {
    return count;
  }

  @JsonProperty("count")
  public void setCount(int count) {
    this.count = count;
  }
}