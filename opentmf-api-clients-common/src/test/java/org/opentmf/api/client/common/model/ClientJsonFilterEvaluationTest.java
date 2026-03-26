package org.opentmf.api.client.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive verification of Jayway JsonPath 3.x filter expressions.
 *
 * <p>These tests document the behaviour of every filter syntax pattern that users of
 * opentmf-api-clients are likely to need.  They run against two static JSON documents –
 * one with TMF-style characteristic arrays and one with simple numeric data – so that
 * each pattern is validated end-to-end with the exact Jayway version on the classpath.
 *
 * @see <a href="https://github.com/json-path/JsonPath#filter-operators">Jayway JsonPath
 *     filter operators</a>
 */
class ClientJsonFilterEvaluationTest {

  private static final String JSON = """
      [
        {
          "name": "Item-0",
          "status": "active",
          "characteristic": [
            {"name": "IMEI", "value": "123456789012345"},
            {"name": "Color", "value": "Black"}
          ]
        },
        {
          "name": "Item-1",
          "status": "inactive",
          "characteristic": [
            {"name": "IMEI", "value": "999888777666555"},
            {"name": "Color", "value": "White"}
          ]
        },
        {
          "name": "Item-2",
          "status": "active",
          "characteristic": [
            {"name": "SerialNumber", "value": "SN001"}
          ]
        },
        {
          "name": "Item-3",
          "status": "active",
          "characteristic": [
            {"name": "IMEI", "value": "123456789012345"},
            {"name": "Color", "value": "Red"}
          ]
        },
        {
          "name": "Item-4",
          "status": "suspended",
          "characteristic": [
            {"name": "SerialNumber", "value": "SN002"},
            {"name": "Model", "value": "X100"}
          ]
        }
      ]
      """;

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private List<String> names(String filter) {
    List<Map<String, Object>> hits = JsonPath.read(JSON, filter);
    return hits.stream().map(m -> (String) m.get("name")).toList();
  }

  // =======================================================================
  //  1.  Simple field equality & comparison
  // =======================================================================

  @Nested
  class SimpleFieldFilters {

    @Test
    void equality() {
      assertThat(names("$[?(@.status == 'active')]"))
          .containsExactly("Item-0", "Item-2", "Item-3");
    }

    @Test
    void notEqual() {
      assertThat(names("$[?(@.status != 'active')]"))
          .containsExactly("Item-1", "Item-4");
    }

    @Test
    void comparisonOperators() {
      String numJson = "[{\"n\":\"A\",\"p\":5},{\"n\":\"B\",\"p\":10},{\"n\":\"C\",\"p\":15}]";
      List<Map<String, Object>> gt = JsonPath.read(numJson, "$[?(@.p > 10)]");
      assertThat(gt).hasSize(1);
      List<Map<String, Object>> gte = JsonPath.read(numJson, "$[?(@.p >= 10)]");
      assertThat(gte).hasSize(2);
      List<Map<String, Object>> lt = JsonPath.read(numJson, "$[?(@.p < 10)]");
      assertThat(lt).hasSize(1);
      List<Map<String, Object>> lte = JsonPath.read(numJson, "$[?(@.p <= 10)]");
      assertThat(lte).hasSize(2);
    }
  }

  // =======================================================================
  //  2.  Logical operators  (&&, ||)
  // =======================================================================

  @Nested
  class LogicalOperators {

    @Test
    void and() {
      assertThat(names("$[?(@.status == 'active' && @.name == 'Item-0')]"))
          .containsExactly("Item-0");
    }

    @Test
    void or() {
      assertThat(names("$[?(@.status == 'active' || @.status == 'suspended')]"))
          .containsExactly("Item-0", "Item-2", "Item-3", "Item-4");
    }

    @Test
    void combinedAndOr() {
      assertThat(names(
          "$[?((@.status == 'active' || @.status == 'suspended') && @.name == 'Item-4')]"))
          .containsExactly("Item-4");
    }
  }

  // =======================================================================
  //  3.  Regex match  (=~)
  // =======================================================================

  @Nested
  class RegexMatch {

    @Test
    void matchesPattern() {
      assertThat(names("$[?(@.name =~ /Item-[03]/)]"))
          .containsExactly("Item-0", "Item-3");
    }

    @Test
    void caseInsensitive() {
      assertThat(names("$[?(@.status =~ /ACTIVE/i)]"))
          .containsExactly("Item-0", "Item-2", "Item-3");
    }
  }

  // =======================================================================
  //  4.  IN / NIN operators
  // =======================================================================

  @Nested
  class InOperators {

    @Test
    void inList() {
      assertThat(names("$[?(@.status IN ['active','suspended'])]"))
          .containsExactly("Item-0", "Item-2", "Item-3", "Item-4");
    }

    @Test
    void notInList() {
      assertThat(names("$[?(@.status NIN ['inactive','suspended'])]"))
          .containsExactly("Item-0", "Item-2", "Item-3");
    }
  }

  // =======================================================================
  //  5.  Exists / negated exists
  // =======================================================================

  @Nested
  class ExistsCheck {

    @Test
    void fieldExists() {
      assertThat(names("$[?(@.characteristic)]")).hasSize(5);
    }

    @Test
    void negatedFieldExists() {
      assertThat(names("$[?(!@.missingField)]")).hasSize(5);
    }
  }

  // =======================================================================
  //  6.  SIZE and EMPTY operators
  // =======================================================================

  @Nested
  class SizeAndEmpty {

    @Test
    void sizeExact() {
      assertThat(names("$[?(@.characteristic size 1)]"))
          .containsExactly("Item-2");
      assertThat(names("$[?(@.characteristic size 2)]"))
          .containsExactly("Item-0", "Item-1", "Item-3", "Item-4");
    }

    @Test
    void emptyTrue() {
      String j = "[{\"tags\":[]},{\"tags\":[\"a\"]},{\"tags\":[\"b\",\"c\"]}]";
      List<Map<String, Object>> hits = JsonPath.read(j, "$[?(@.tags empty true)]");
      assertThat(hits).hasSize(1);
    }

    @Test
    void emptyFalse() {
      String j = "[{\"tags\":[]},{\"tags\":[\"a\"]},{\"tags\":[\"b\",\"c\"]}]";
      List<Map<String, Object>> hits = JsonPath.read(j, "$[?(@.tags empty false)]");
      assertThat(hits).hasSize(2);
    }
  }

  // =======================================================================
  //  7.  Single-field matching in nested arrays
  //      (IN on projected values, CONTAINS, regex on projected values)
  // =======================================================================

  @Nested
  class SingleFieldNestedArray {

    @Test
    void inProjectedValues() {
      assertThat(names("$[?('IMEI' in @.characteristic[*].name)]"))
          .containsExactly("Item-0", "Item-1", "Item-3");
    }

    @Test
    void containsOnProjectedValues() {
      assertThat(names("$[?(@.characteristic[*].name CONTAINS 'IMEI')]"))
          .containsExactly("Item-0", "Item-1", "Item-3");
    }

    @Test
    void regexOnProjectedValues() {
      assertThat(names("$[?(@.characteristic[*].name =~ /Serial.*/)]"))
          .containsExactly("Item-2", "Item-4");
    }
  }

  // =======================================================================
  //  8.  CORRELATED multi-field matching in nested arrays
  //
  //      This is the most important section.  "Correlated" means BOTH
  //      conditions must be satisfied by THE SAME nested object, not just
  //      by any two different objects in the array.
  //
  //      In Jayway 3.x the nested filter [?()]  inside an outer [?()]
  //      always resolves to an array (even an empty one), which the outer
  //      exists-check treats as "truthy".  You MUST use `empty false` to
  //      explicitly check that the inner filter produced at least one hit.
  // =======================================================================

  @Nested
  class CorrelatedNestedArrayFilter {

    private static final String IMEI_FILTER =
        "@.name=='IMEI' && @.value=='123456789012345'";

    @Test
    void emptyFalse_correctlyFilters() {
      assertThat(names(
          "$[?(@.characteristic[?(" + IMEI_FILTER + ")] empty false)]"))
          .containsExactly("Item-0", "Item-3");
    }

    @Test
    void naiveNestedFilter_doesNotWork() {
      // Without 'empty false', the inner [?(...)] always resolves to an
      // array, making the outer exists-check pass for every item.
      assertThat(names(
          "$[?(@.characteristic[?(" + IMEI_FILTER + ")])]"))
          .hasSize(5);
    }

    @Test
    void negatedSizeZero_alsoWorks() {
      // Alternative syntax: "the inner result array is NOT of size 0"
      assertThat(names(
          "$[?(!(@.characteristic[?(" + IMEI_FILTER + ")] size 0))]"))
          .containsExactly("Item-0", "Item-3");
    }

    @Test
    void noMatch_returnsEmpty() {
      assertThat(names(
          "$[?(@.characteristic[?(@.name=='IMEI' && @.value=='000')] empty false)]"))
          .isEmpty();
    }

    @Test
    void singleFieldOnly_matchesAllWithThatName() {
      assertThat(names(
          "$[?(@.characteristic[?(@.name=='IMEI')] empty false)]"))
          .containsExactly("Item-0", "Item-1", "Item-3");
    }
  }

  // =======================================================================
  //  9.  Uncorrelated multi-field matching
  //      (IN on projected values -- can give FALSE POSITIVES)
  // =======================================================================

  @Nested
  class UncorrelatedNestedArrayFilter {

    @Test
    void inProjectedValues_matchesCorrectly_whenDataDoesNotOverlap() {
      // For this specific dataset the uncorrelated approach happens to
      // give the same answer as the correlated one, but beware: if one
      // object had name=IMEI in one entry and value=123... in a DIFFERENT
      // entry it would be a false positive.
      assertThat(names(
          "$[?('IMEI' in @.characteristic[*].name"
          + " && '123456789012345' in @.characteristic[*].value)]"))
          .containsExactly("Item-0", "Item-3");
    }

    @Test
    void inProjectedValues_falsePositive_demo() {
      // Add an item that has name=IMEI on one entry and the target value
      // on a DIFFERENT entry → the uncorrelated filter is fooled.
      String trickJson = """
          [
            {
              "name": "Tricky",
              "characteristic": [
                {"name": "IMEI", "value": "OTHER"},
                {"name": "Color", "value": "123456789012345"}
              ]
            }
          ]
          """;
      List<Map<String, Object>> uncorrelated = JsonPath.read(trickJson,
          "$[?('IMEI' in @.characteristic[*].name"
          + " && '123456789012345' in @.characteristic[*].value)]");
      assertThat(uncorrelated).hasSize(1); // FALSE POSITIVE

      List<Map<String, Object>> correlated = JsonPath.read(trickJson,
          "$[?(@.characteristic[?(@.name=='IMEI'"
          + " && @.value=='123456789012345')] empty false)]");
      assertThat(correlated).isEmpty(); // Correctly excluded
    }
  }
}
