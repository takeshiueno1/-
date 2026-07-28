package jp.co.query.attendance.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DemoEmployeeNamesTest {

    @Test
    void providesFiftyUniqueNaturalNamesIncludingTakeshiUeno() {
        var names = IntStream.rangeClosed(1, DemoEmployeeNames.size())
                .mapToObj(DemoEmployeeNames::get)
                .toList();

        assertThat(names).hasSize(50);
        assertThat(new HashSet<>(names)).hasSize(50);
        assertThat(names.getFirst()).isEqualTo("上野 豪");
    }

    @Test
    void rejectsOutOfRangeEmployeeNumber() {
        assertThatThrownBy(() -> DemoEmployeeNames.get(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DemoEmployeeNames.get(51))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
