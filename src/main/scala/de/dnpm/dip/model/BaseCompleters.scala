package de.dnpm.dip.model


import cats.{
  Applicative,
  Id
}
import shapeless.{
  Coproduct,
  :+:,
  CNil
}
import de.dnpm.dip.util.Completer
import de.dnpm.dip.util.Tree
import de.dnpm.dip.coding.{
  Code,
  Coding,
  CodeSystem,
  CodeSystemProvider,
  CodeSystemProviders,
  UnregisteredMedication
}
import de.dnpm.dip.coding.hgvs.HGVS
import de.dnpm.dip.coding.atc.ATC
import de.dnpm.dip.coding.UnregisteredMedication



trait BaseCompleters
{

  import Completer.syntax._


  implicit val patientCompleter: Completer[Patient] =
    Completer.of(
      pat =>
        pat.copy(
          gender       = pat.gender.complete,
          managingSite = Some(Site.local)
        )
    )


  implicit def hgvsCompleter[S <: HGVS]: Completer[Coding[S]] =
    Completer.of(
      coding => coding.copy(
        display = coding.display.orElse(Some(coding.code.value))
      )
    )


  implicit val unregisteredCodingCompleter: Completer[Coding[UnregisteredMedication]] =
    Completer.of(
      coding => coding.copy(
        display = coding.display.orElse(Some(coding.code.value))
      )
    )

/*
  implicit def medicationsCodingExpander(
    implicit csp: CodeSystemProvider[ATC,Id,Applicative[Id]]
  ): Tree.Expander[Coding[Medications]] = {

    import Tree.Expander.syntax._

    coding =>
      coding.system match {
        case sys if sys == Coding.System[ATC].uri =>
          coding.asInstanceOf[Coding[ATC]]
           .expand
           .asInstanceOf[Tree[Coding[Medications]]]

        case _ => Tree(coding.complete)
      }
  }
*/

  implicit def coproductCodingCompleter[
    H: Coding.System,
    T <: Coproduct
  ](
    implicit
    compH: Completer[Coding[H]],
    compT: Completer[Coding[T]]
  ): Completer[Coding[H :+: T]] =
    Completer.of {
      coding =>
        (
          if (coding.system == Coding.System[H].uri)
            compH(coding.asInstanceOf[Coding[H]])
          else
            compT(coding.asInstanceOf[Coding[T]])
        )
        .asInstanceOf[Coding[H :+: T]]
    }

  implicit def terminalCoproductCodingCompleter[
    H: Coding.System
  ](
    implicit
    compH: Completer[Coding[H]],
  ): Completer[Coding[H :+: CNil]] =
    compH.asInstanceOf[Completer[Coding[H :+: CNil]]]



  @deprecated
  private def expandDescendantCodings[T,U >: T](
    code: Code[T],
    cs: CodeSystem[U]
  ): Set[Coding[T]] =
    (cs.concept(code).toSet ++ cs.descendantsOf(code))
      .map(
        _.toCoding(cs.uri)
         .asInstanceOf[Coding[T]]
      )


  @deprecated
  private def expandDescendants[T,U >: T](
    coding: Coding[T],
    cs: CodeSystem[U]
  ): Set[Coding[T]] =
    (cs.concept(coding.code).toSet ++ cs.descendantsOf(coding.code))
      .map(
        _.toCoding(coding.system)
         .asInstanceOf[Coding[T]]
      )

  @deprecated
  def expandDescendants[T,U >: T](
    coding: Coding[T],
    csp: CodeSystemProvider[U,Id,Applicative[Id]]
  ): Set[Coding[T]] =
    expandDescendants(
      coding,
      coding.version
        .flatMap(csp.get)
        .getOrElse(csp.latest)
    )


  @deprecated
  def expandDescendantCodings[T: Coding.System](
    code: Code[T]
  )(
    implicit csp: => CodeSystemProvider[T,Id,Applicative[Id]]
  ): Set[Coding[T]] = {

    val cs = csp.latest

    (cs.concept(code).toSet ++ cs.descendantsOf(code))
      .map(_.toCoding)
  }

  @deprecated
  def expandDescendants[T](
    coding: Coding[T]
  )(
    implicit csp: => CodeSystemProvider[T,Id,Applicative[Id]]
  ): Set[Coding[T]] =
    expandDescendants(
      coding,
      coding.version
        .flatMap(csp.get)
        .getOrElse(csp.latest)
    )


  // By-name csp parameter (i.e. "lazy" as only evaluated upon being referenced)
  // is required because in traits, the value is usually not yet initialized at this point,
  // resulting in weird null pointer exception
  @deprecated
  def descendantExpander[T: Coding.System](
    implicit csp: => CodeSystemProvider[T,Id,Applicative[Id]]
  ): Completer[Set[Coding[T]]] =
    Completer.of(
      _.flatMap(coding => expandDescendants(coding,csp))
    )


  // By-name csps parameter (i.e. "lazy" as only evaluated upon being referenced)
  // is required because in traits, the value is usually not yet initialized at this point,
  // resulting in weird null pointer exception
  @deprecated
  def descendantExpanderOf[CS <: Coproduct](
    implicit csps: => CodeSystemProviders[CS]
  ): Completer[Set[Coding[CS]]] =
    Completer.of(
      _.flatMap(
        coding =>
          expandDescendants(
            coding,
            csps.values(coding.system))  // Map.apply safe here, because the code won't compile
          )                              // if not all CodeSystemProviders are in scope, so csps is sure to contain all systems
    )

}
