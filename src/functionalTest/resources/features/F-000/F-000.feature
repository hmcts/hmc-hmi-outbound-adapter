#====================================
@F-000
Feature: F-000: Healthcheck Operation
#====================================

#-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  @S-000 @Smoke
  Scenario: must return a successful response from the Healthcheck Operation

    Given a request is prepared with appropriate values
    When it is submitted to call the [Healthcheck] operation of [HMC HMI Outbound Adapter]

    Then a positive response is received
    And the response [has the 200 OK code]
    And the response has all other details as expected.

#-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
