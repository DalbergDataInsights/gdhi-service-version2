package it.gdhi.dto;

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
    public String getResponseId() {
       if (responseId == null || responseId.isEmpty()){
           responseId = randomUUID().toString();
       }
       System.out.println("ResponseId: " + responseId);
       return responseId;
   }

}