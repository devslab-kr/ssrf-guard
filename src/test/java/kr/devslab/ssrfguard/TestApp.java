package kr.devslab.ssrfguard;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap class for {@code @SpringBootTest}. The starter itself ships no
 * {@code @SpringBootApplication} so tests need one for context lookup —
 * sitting at the root of the {@code kr.devslab.ssrfguard} package lets every
 * test under {@code src/test/java/kr/devslab/ssrfguard/**} find it on the
 * upward scan.
 */
@SpringBootApplication
public class TestApp {
}
