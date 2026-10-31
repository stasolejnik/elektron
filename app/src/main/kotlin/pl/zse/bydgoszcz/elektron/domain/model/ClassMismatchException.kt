package pl.zse.bydgoszcz.elektron.domain.model

import java.io.IOException

/**
 * Strona planu pod identyfikatorem wybranej klasy należy do innej klasy (przenumerowane klasy, nowy
 * rok szkolny). Plan nie jest zapisywany - SyncCoordinator sprawdza listę klas.
 */
class ClassMismatchException(expected: String, actual: String) :
    IOException("Plan klasy $actual zamiast $expected")
