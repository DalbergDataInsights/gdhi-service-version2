package it.gdhi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static java.util.UUID.randomUUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor

public class AIRequest {
   private String responseId;
   private String query;
   @JsonProperty("user_language")
   private String userLanguage;

    public String getResponseId() {
       if (responseId == null || responseId.isEmpty()){
           responseId = randomUUID().toString();
       }
       return responseId;
   }

}
