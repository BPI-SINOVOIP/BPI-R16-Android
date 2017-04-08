function pbc_check_env
{
if [ -z "$PBAP_SERVER" ] 
then
   echo "PBAP_SERVER not set"
   exit 1
fi    

if [ -z "$PBAP_CLIENT_SERVICE_API" ] 
then
   echo "PBAP_SERVER not set. Please use source setenv.sh <bdaddr>"
   exit 1
fi    
}

function dbg_print_cmd
{

if [ "$PBC_DEBUG" ] 
then
   eval "echo $PBAP_CLIENT_SERVICE_API" 
fi    
}

pbc_check_env
dbg_print_cmd
eval $PBAP_CLIENT_SERVICE_API

