## Table of contents

1. [Installation](#installation)
2. [Utilisation](#utilisation)
3. [Remarques](#remarques)

## Installation
1. Installez Gradle.
2. Clonez la branche `feature/protobuf-test` du projet.

## Utilisation
1. Rendez-vous dans le répertoire `tests/src/main/proto/` et placez votre fichier `*.proto`.
2. Dans la classe `generator\src\main\java\org\sudu\protogen\generator\field\FieldGenerator.java`, mettez les chemins des projets d'origine qui contiennent les classes DTO. (Cela afin de tester la visibilité des attributs dans les classes d'origine, et puis décider si l'utilisation des accesseurs et mutateurs est nécessaire)
2. Ouvrez la ligne de commande et accédez au répertoire du projet.
3. Assurez-vous que vous utilisez la `jdk-17`.
4. Exécutez la commande suivante pour le projet avec Gradle : `gradle clean build`.

## Remarques
Pour garantir le bon fonctionnement de ProtoGen sans aucune erreur, il est essentiel de respecter les conventions suivantes :
1. Il est nécessaire d'inclure les deux lignes suivantes en tête du fichier proto :
```protobuf
import "protogen/options.proto";

option (protogen.enable) = true;
```
2. Les noms des messages doivent respecter le format de nommage `Sudu`, c'est-à-dire que tous les noms de messages doivent commencer par `Grpc`.
3. Les noms des attributs à l'intérieur des messages ne doivent pas débuter par des caractères spéciaux (y compris l'underscore) ni par des chiffres.
4. Un message qui définit des attributs ayant le même nom que le message n'est pas acceptable.
5. La multi-hiérarchie des messages n'est pas supportée par ProtoGen. Cela signifie qu'il faut avoir chaque message de manière indépendante et non à l'intérieur d'un autre message. (Cela fonctionne avec un niveau, mais à partir de deux niveaux, cela ne fonctionne pas.)
